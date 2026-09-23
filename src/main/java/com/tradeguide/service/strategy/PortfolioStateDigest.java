package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PremarketGuideCandidateSource;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.AssetKey;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.CandidateRef;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 결정 입력의 결정적 SHA-256. 내용 필드만 직렬화하고 생성·수정 시각은 제외해 같은 값의
 * 재저장이 다이제스트를 바꾸지 않게 한다. 금액은 캔들 해시 v2와 같이 표기가 아니라 값으로 직렬화한다.
 * 원장에는 거래 id를 포함해 삭제 후 같은 내용 재등록도 다른 원장으로 본다.
 */
@Component
public class PortfolioStateDigest {

    public static final int SCHEMA_VERSION = 1;

    private static final Comparator<AssetKey> ASSET_ORDER =
            Comparator.comparing((AssetKey key) -> key.market().name()).thenComparing(AssetKey::ticker);

    public String ledger(List<TradeTransaction> transactions) {
        return sha256("ledger", output -> {
            List<TradeTransaction> sorted = transactions.stream()
                    .sorted(Comparator.comparing(TradeTransaction::getId))
                    .toList();
            output.writeInt(sorted.size());
            for (TradeTransaction transaction : sorted) {
                output.writeLong(transaction.getId());
                output.writeUTF(transaction.getMarket().name());
                output.writeUTF(transaction.getTicker());
                output.writeUTF(transaction.getTradeType().name());
                writeDecimal(output, transaction.getQuantity());
                writeDecimal(output, transaction.getExecutedPrice());
                writeDecimal(output, transaction.getFee());
                output.writeLong(transaction.getTradedAt().getEpochSecond());
                output.writeInt(transaction.getTradedAt().getNano());
                writeNullable(output, transaction.getSource() == null ? null : transaction.getSource().name());
            }
        });
    }

    public String holdings(List<Holding> holdings) {
        return sha256("holdings", output -> {
            List<Holding> sorted = holdings.stream()
                    .sorted(Comparator.comparing(AssetKey::of, ASSET_ORDER))
                    .toList();
            output.writeInt(sorted.size());
            for (Holding holding : sorted) {
                output.writeUTF(holding.getMarket().name());
                output.writeUTF(holding.getTicker());
                writeDecimal(output, holding.getQuantity());
                writeDecimal(output, holding.getAveragePurchasePrice());
            }
        });
    }

    public String strategyOverrides(Map<AssetKey, InvestmentTrack> overrides) {
        return sha256("strategy-overrides", output -> {
            List<AssetKey> keys = overrides.keySet().stream().sorted(ASSET_ORDER).toList();
            output.writeInt(keys.size());
            for (AssetKey key : keys) {
                writeAssetKey(output, key);
                output.writeUTF(overrides.get(key).name());
            }
        });
    }

    /** 장전 가이드가 쓰는 손절 비율만 포함한다. 최대 손실·노출 비율은 가이드 입력이 아니다. */
    public String riskSettings(BigDecimal portfolioStopLossRatio, Map<AssetKey, BigDecimal> stopLossOverrides) {
        return sha256("risk-settings", output -> {
            writeDecimal(output, portfolioStopLossRatio);
            List<AssetKey> keys = stopLossOverrides.keySet().stream().sorted(ASSET_ORDER).toList();
            output.writeInt(keys.size());
            for (AssetKey key : keys) {
                writeAssetKey(output, key);
                writeDecimal(output, stopLossOverrides.get(key));
            }
        });
    }

    public String candidates(PremarketGuideCandidateSource source, List<CandidateRef> candidates) {
        return sha256("candidates", output -> {
            output.writeUTF(source.name());
            List<CandidateRef> sorted = candidates.stream()
                    .sorted(Comparator.comparing((CandidateRef ref) -> ref.market().name())
                            .thenComparing(CandidateRef::ticker))
                    .toList();
            output.writeInt(sorted.size());
            for (CandidateRef candidate : sorted) {
                output.writeUTF(candidate.market().name());
                output.writeUTF(candidate.ticker());
                writeNullable(output, candidate.investmentTrack() == null ? null : candidate.investmentTrack().name());
            }
        });
    }

    public String assetCatalog(List<AssetProfile> profiles) {
        return sha256("asset-catalog", output -> {
            List<AssetProfile> sorted = profiles.stream()
                    .sorted(Comparator.comparing((AssetProfile profile) -> profile.getMarket().name())
                            .thenComparing(AssetProfile::getTicker))
                    .toList();
            output.writeInt(sorted.size());
            for (AssetProfile profile : sorted) {
                output.writeUTF(profile.getMarket().name());
                output.writeUTF(profile.getTicker());
                output.writeUTF(profile.getInvestmentTrack().name());
            }
        });
    }

    public String state(String ledgerSha256, String holdingsSha256, String strategyOverridesSha256,
                        String riskSettingsSha256, String candidateSetSha256, String assetCatalogSha256,
                        Long brokerSnapshotId, boolean brokerSnapshotHasItems) {
        return sha256("state", output -> {
            output.writeUTF(ledgerSha256);
            output.writeUTF(holdingsSha256);
            output.writeUTF(strategyOverridesSha256);
            output.writeUTF(riskSettingsSha256);
            output.writeUTF(candidateSetSha256);
            output.writeUTF(assetCatalogSha256);
            output.writeBoolean(brokerSnapshotId != null);
            if (brokerSnapshotId != null) {
                output.writeLong(brokerSnapshotId);
            }
            output.writeBoolean(brokerSnapshotHasItems);
        });
    }

    private void writeAssetKey(DataOutputStream output, AssetKey key) throws IOException {
        output.writeUTF(key.market().name());
        output.writeUTF(key.ticker());
    }

    private void writeDecimal(DataOutputStream output, BigDecimal value) throws IOException {
        writeNullable(output, value == null ? null : value.stripTrailingZeros().toPlainString());
    }

    private void writeNullable(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeUTF(value);
        }
    }

    private String sha256(String component, Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(SCHEMA_VERSION);
            output.writeUTF(component);
            writer.write(output);
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("포트폴리오 상태 해시를 계산할 수 없습니다.", exception);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}
