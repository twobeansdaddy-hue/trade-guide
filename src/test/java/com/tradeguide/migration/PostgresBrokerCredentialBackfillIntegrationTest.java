package com.tradeguide.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17이 기존 연결의 자격 증명을 새 키/값 표로 옮기는지 실제 PostgreSQL에서 확인한다.
 *
 * <p>Spring 컨텍스트를 띄우지 않고 Flyway API를 직접 쓰는 이유는 순서 때문이다. 백필을
 * 검증하려면 <b>V17 이전 스키마에 옛 행이 있어야</b> 하는데, 애플리케이션 기동은 모든
 * 마이그레이션을 한 번에 적용하므로 그 중간 지점에 데이터를 넣을 수 없다.
 * 그래서 V16까지 적용 → 옛 행 삽입 → 나머지 적용 순으로 나눠 실행한다.
 *
 * <p>픽스처는 고정된 더미 문자열이다. 실제 자격 증명도, 실제 암호문도 쓰지 않는다.
 * 이 마이그레이션은 암호문을 복호화하거나 재암호화하지 않으므로 더미 값으로 충분하고,
 * 그 사실 자체가 "암호화 키 없는 환경에서도 마이그레이션이 실행된다"는 이 슬라이스의
 * 판단을 증명한다.
 */
@Tag("postgres")
@Testcontainers
class PostgresBrokerCredentialBackfillIntegrationTest {

    /** 자격 증명 일반화 직전 버전이다. 여기까지 적용한 뒤 옛 형태의 행을 심는다. */
    private static final String VERSION_BEFORE_BACKFILL = "16";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void movesExistingClientCredentialsIntoTheKeyValueTableWithoutTouchingCiphertext() throws SQLException {
        migrate(MigrationVersion.fromVersion(VERSION_BEFORE_BACKFILL));

        long brokerConnectionId;
        try (Connection connection = openConnection()) {
            long memberId = insertMember(connection);
            brokerConnectionId = insertBrokerConnection(connection, memberId);
            insertLegacySecret(connection, brokerConnectionId);
        }

        migrate(MigrationVersion.LATEST);

        try (Connection connection = openConnection()) {
            // 암호문·초기화 벡터·키 버전이 값 그대로 옮겨져야 한다. 하나라도 바뀌면 복호화가 깨진다.
            assertThat(credentialRows(connection, brokerConnectionId)).containsExactly(
                    "clientId|dummy-ciphertext-client-id|dummy-iv-client-id|1",
                    "clientSecret|dummy-ciphertext-client-secret|dummy-iv-client-secret|1"
            );
        }
    }

    private void migrate(MigrationVersion targetVersion) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private List<String> credentialRows(Connection connection, long brokerConnectionId) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT field_key, ciphertext, initialization_vector, encryption_key_version
                FROM broker_connection_secret_values
                WHERE broker_connection_id = ?
                ORDER BY field_key
                """)) {
            statement.setLong(1, brokerConnectionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(resultSet.getString(1)
                            + "|" + resultSet.getString(2)
                            + "|" + resultSet.getString(3)
                            + "|" + resultSet.getInt(4));
                }
            }
        }
        return rows;
    }

    private long insertMember(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO members (email, nickname, created_at) VALUES (?, ?, NOW()) RETURNING id")) {
            statement.setString(1, "backfill@example.com");
            statement.setString(2, "backfill-user");
            return generatedId(statement);
        }
    }

    private long insertBrokerConnection(Connection connection, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO broker_connections
                    (member_id, provider, display_name, status, created_at, updated_at)
                VALUES (?, 'TOSS_SECURITIES', '개인 토스증권', 'UNVERIFIED', NOW(), NOW())
                RETURNING id
                """)) {
            statement.setLong(1, memberId);
            return generatedId(statement);
        }
    }

    private void insertLegacySecret(Connection connection, long brokerConnectionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO broker_connection_secrets
                    (broker_connection_id, encrypted_client_id, client_id_initialization_vector,
                     encrypted_client_secret, client_secret_initialization_vector, encryption_key_version)
                VALUES (?, 'dummy-ciphertext-client-id', 'dummy-iv-client-id',
                        'dummy-ciphertext-client-secret', 'dummy-iv-client-secret', 1)
                """)) {
            statement.setLong(1, brokerConnectionId);
            statement.executeUpdate();
        }
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
