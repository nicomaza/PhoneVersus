package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaComparisonResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaCompetitorPricesResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaDifferencesResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaMetricResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaMinePricesResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaProductResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaSummaryResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteColorStockResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaMoney;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaPrices;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaCatalogNotAvailableException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaMatchAmbiguousException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaProductNotFoundException;
import celulares.cordobacelulares.integration.liberadosya.service.LiberadosYaCatalogService;
import celulares.cordobacelulares.services.TiendaPorteService;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BombaiComparativaService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal COMPETITIVE_THRESHOLD = new BigDecimal("0.02");
    private static final int MONEY_SCALE = 2;

    private static final String MATCHED = "MATCHED";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String AMBIGUOUS = "AMBIGUOUS";
    private static final String CATALOG_UNAVAILABLE = "CATALOG_UNAVAILABLE";
    private static final String MATCH_ERROR = "ERROR";

    private static final String CHEAPER = "CHEAPER";
    private static final String MORE_EXPENSIVE = "MORE_EXPENSIVE";
    private static final String EQUAL = "EQUAL";
    private static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    private static final String CHEAPER_ALL = "CHEAPER_ALL";
    private static final String MORE_EXPENSIVE_ALL = "MORE_EXPENSIVE_ALL";
    private static final String MIXED = "MIXED";
    private static final String COMPETITIVE = "COMPETITIVE";
    private static final String INCOMPLETE = "INCOMPLETE";

    private final TiendaPorteService tiendaPorteService;
    private final LiberadosYaCatalogService liberadosYaCatalogService;

    public BombaiComparativaService(
            TiendaPorteService tiendaPorteService,
            LiberadosYaCatalogService liberadosYaCatalogService
    ) {
        this.tiendaPorteService = tiendaPorteService;
        this.liberadosYaCatalogService = liberadosYaCatalogService;
    }

    public BombaiComparativaResponse getComparativa() {
        List<MineProduct> mineProducts = ownProducts();
        Map<String, MatchProjection> matchCache = new HashMap<>();
        List<BombaiComparativaProductResponse> rows = mineProducts.stream()
                .map(product -> toRow(product, matchCache))
                .sorted(defaultComparator())
                .toList();

        return new BombaiComparativaResponse(summary(rows), rows);
    }

    private List<MineProduct> ownProducts() {
        List<TiendaPorteBrandResponse> catalog = tiendaPorteService.getAll(null, null);
        List<MineProduct> products = new ArrayList<>();
        if (catalog == null) {
            return products;
        }

        for (TiendaPorteBrandResponse brand : catalog) {
            if (brand == null || brand.getModelos() == null) {
                continue;
            }
            String brandName = safeText(brand.getMarca(), "Sin marca");
            for (TiendaPorteModelResponse model : brand.getModelos()) {
                if (model == null || isBlank(model.getModeloNombre())) {
                    continue;
                }
                BombaiComparativaMinePricesResponse mine = new BombaiComparativaMinePricesResponse(
                        positiveOrNull(model.getPrecioUsd()),
                        positiveOrNull(model.getPrecioUsd()),
                        positiveOrNull(model.getPrecioPesos()),
                        positiveOrNull(model.getPrecioTransferenciaBancaria()),
                        positiveOrNull(model.getPrecioTarjeta6Pagos())
                );
                products.add(new MineProduct(
                        brandName,
                        model.getModeloNombre().trim(),
                        model.getOrigen() == null ? null : model.getOrigen().name(),
                        colorCount(model.getColores()),
                        mine
                ));
            }
        }
        return products;
    }

    private BombaiComparativaProductResponse toRow(
            MineProduct mineProduct,
            Map<String, MatchProjection> matchCache
    ) {
        MatchProjection match = matchCache.computeIfAbsent(
                matchKey(mineProduct.brand(), mineProduct.model()),
                ignored -> matchLiberadosYa(mineProduct.brand(), mineProduct.model())
        );
        BombaiComparativaCompetitorPricesResponse competitor = match.competitor();
        BombaiComparativaDifferencesResponse differences = differences(mineProduct.mine(), competitor);
        BombaiComparativaComparisonResponse comparison = comparison(mineProduct.mine(), competitor, differences);

        return new BombaiComparativaProductResponse(
                mineProduct.brand(),
                mineProduct.model(),
                mineProduct.brand() + " " + mineProduct.model(),
                mineProduct.origin(),
                mineProduct.colorCount(),
                match.status(),
                match.message(),
                mineProduct.mine(),
                competitor,
                differences,
                comparison
        );
    }

    private MatchProjection matchLiberadosYa(String brand, String model) {
        try {
            LiberadosYaProduct product = liberadosYaCatalogService.findMatch(brand, model);
            return new MatchProjection(MATCHED, null, competitor(product));
        } catch (LiberadosYaProductNotFoundException ex) {
            return MatchProjection.empty(NOT_FOUND, "Sin coincidencia en LiberadosYa");
        } catch (LiberadosYaMatchAmbiguousException ex) {
            return MatchProjection.empty(AMBIGUOUS, "Coincidencia ambigua en LiberadosYa");
        } catch (LiberadosYaCatalogNotAvailableException ex) {
            return MatchProjection.empty(CATALOG_UNAVAILABLE, "Catalogo de LiberadosYa no disponible");
        } catch (RuntimeException ex) {
            return MatchProjection.empty(MATCH_ERROR, "No se pudo comparar este producto");
        }
    }

    private BombaiComparativaCompetitorPricesResponse competitor(LiberadosYaProduct product) {
        LiberadosYaPrices prices = product == null ? null : product.getPrecios();
        LiberadosYaMoney ars = prices == null ? null : prices.getArs();
        LiberadosYaMoney usd = prices == null ? null : prices.getUsd();
        BigDecimal cashArs = money(ars == null ? null : ars.getEfectivo());
        BigDecimal currentArs = money(ars == null ? null : ars.getActual());
        BigDecimal listArs = money(ars == null ? null : ars.getLista());
        BigDecimal transferArs = currentArs;
        BigDecimal cardArs = null;
        BigDecimal cashUsd = money(usd == null ? null : usd.getEfectivo());
        BigDecimal currentUsd = money(usd == null ? null : usd.getActual());
        BigDecimal listUsd = money(usd == null ? null : usd.getLista());

        return new BombaiComparativaCompetitorPricesResponse(
                true,
                product == null ? null : firstNonBlank(product.getVersionComercial(), product.getNombre()),
                cashArs,
                currentArs,
                listArs,
                transferArs,
                cardArs,
                cashUsd,
                currentUsd,
                listUsd,
                firstPositive(cashUsd, currentUsd, listUsd)
        );
    }

    private BombaiComparativaDifferencesResponse differences(
            BombaiComparativaMinePricesResponse mine,
            BombaiComparativaCompetitorPricesResponse competitor
    ) {
        return new BombaiComparativaDifferencesResponse(
                difference(mine == null ? null : mine.getCashArs(), competitor == null ? null : competitor.getCashArs()),
                difference(mine == null ? null : mine.getTransferArs(), competitor == null ? null : competitor.getTransferArs()),
                difference(mine == null ? null : mine.getCardArs(), competitor == null ? null : competitor.getCardArs()),
                difference(mine == null ? null : mine.getPriceUsd(), competitor == null ? null : competitor.getMainUsd())
        );
    }

    private BombaiComparativaComparisonResponse comparison(
            BombaiComparativaMinePricesResponse mine,
            BombaiComparativaCompetitorPricesResponse competitor,
            BombaiComparativaDifferencesResponse differences
    ) {
        String cash = comparisonStatus(differences.getCashArs());
        String transfer = comparisonStatus(differences.getTransferArs());
        String card = comparisonStatus(differences.getCardArs());
        String usd = comparisonStatus(differences.getUsd());
        List<ComparisonValue> values = List.of(
                new ComparisonValue(cash, mine == null ? null : mine.getCashArs(), competitor == null ? null : competitor.getCashArs(), differences.getCashArs()),
                new ComparisonValue(transfer, mine == null ? null : mine.getTransferArs(), competitor == null ? null : competitor.getTransferArs(), differences.getTransferArs()),
                new ComparisonValue(card, mine == null ? null : mine.getCardArs(), competitor == null ? null : competitor.getCardArs(), differences.getCardArs()),
                new ComparisonValue(usd, mine == null ? null : mine.getPriceUsd(), competitor == null ? null : competitor.getMainUsd(), differences.getUsd())
        );
        List<ComparisonValue> comparable = values.stream()
                .filter(value -> !NOT_AVAILABLE.equals(value.status()))
                .toList();
        String overall = overall(comparable);
        return new BombaiComparativaComparisonResponse(cash, transfer, card, usd, overall, comparable.size());
    }

    private String overall(List<ComparisonValue> comparable) {
        if (comparable.isEmpty()) {
            return INCOMPLETE;
        }
        if (comparable.stream().allMatch(this::isCompetitiveDifference)) {
            return COMPETITIVE;
        }

        boolean hasCheaper = comparable.stream().anyMatch(value -> CHEAPER.equals(value.status()));
        boolean hasMoreExpensive = comparable.stream().anyMatch(value -> MORE_EXPENSIVE.equals(value.status()));

        if (hasCheaper && hasMoreExpensive) {
            return MIXED;
        }
        if (hasCheaper) {
            return CHEAPER_ALL;
        }
        if (hasMoreExpensive) {
            return MORE_EXPENSIVE_ALL;
        }
        return COMPETITIVE;
    }

    private boolean isCompetitiveDifference(ComparisonValue value) {
        if (value == null || value.difference() == null || !isPositive(value.competitor())) {
            return false;
        }
        BigDecimal ratio = value.difference().abs().divide(value.competitor(), 8, RoundingMode.HALF_UP);
        return ratio.compareTo(COMPETITIVE_THRESHOLD) <= 0;
    }

    private BombaiComparativaSummaryResponse summary(List<BombaiComparativaProductResponse> rows) {
        int total = rows.size();
        int matched = (int) rows.stream().filter(row -> MATCHED.equals(row.getMatchStatus())).count();
        int comparable = (int) rows.stream().filter(row -> row.getComparison() != null && row.getComparison().getComparableCount() > 0).count();
        int cheaper = countOverall(rows, CHEAPER_ALL);
        int moreExpensive = countOverall(rows, MORE_EXPENSIVE_ALL);
        int mixed = countOverall(rows, MIXED);
        int withoutMatch = (int) rows.stream().filter(row -> !MATCHED.equals(row.getMatchStatus())).count();

        return new BombaiComparativaSummaryResponse(
                metric(rows.stream()
                        .map(row -> percentageIncrease(row.getCompetitor().getCashArs(), row.getCompetitor().getTransferArs()))
                        .toList()),
                metric(rows.stream()
                        .map(row -> percentageIncrease(row.getCompetitor().getCashArs(), row.getCompetitor().getCardArs()))
                        .toList()),
                metric(rows.stream()
                        .map(row -> implicitRate(row.getCompetitor().getCashArs(), row.getMine().getCostUsd()))
                        .toList()),
                metric(rows.stream()
                        .map(row -> competitorCustomerExchangeRate(row.getCompetitor()))
                        .toList()),
                total,
                matched,
                comparable,
                cheaper,
                percentage(cheaper, comparable),
                moreExpensive,
                percentage(moreExpensive, comparable),
                mixed,
                withoutMatch
        );
    }

    private int countOverall(List<BombaiComparativaProductResponse> rows, String status) {
        return (int) rows.stream()
                .filter(row -> row.getComparison() != null && status.equals(row.getComparison().getOverall()))
                .count();
    }

    private BombaiComparativaMetricResponse metric(List<BigDecimal> rawValues) {
        List<BigDecimal> values = rawValues.stream()
                .filter(this::isPositive)
                .sorted()
                .toList();
        if (values.isEmpty()) {
            return new BombaiComparativaMetricResponse(null, null, null, null, 0);
        }

        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(values.size()), MONEY_SCALE, RoundingMode.HALF_UP);
        return new BombaiComparativaMetricResponse(
                average,
                median(values),
                values.get(0).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                values.get(values.size() - 1).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                values.size()
        );
    }

    private BigDecimal median(List<BigDecimal> values) {
        int size = values.size();
        if (size % 2 == 1) {
            return values.get(size / 2).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return values.get(size / 2 - 1)
                .add(values.get(size / 2))
                .divide(BigDecimal.valueOf(2), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageIncrease(BigDecimal base, BigDecimal finalValue) {
        if (!isPositive(base) || !isPositive(finalValue)) {
            return null;
        }
        return finalValue.divide(base, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(ONE_HUNDRED)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal implicitRate(BigDecimal ars, BigDecimal usd) {
        if (!isPositive(ars) || !isPositive(usd)) {
            return null;
        }
        return ars.divide(usd, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal competitorCustomerExchangeRate(BombaiComparativaCompetitorPricesResponse competitor) {
        if (competitor == null) {
            return null;
        }
        BigDecimal cashRate = implicitRate(competitor.getCashArs(), competitor.getCashUsd());
        if (cashRate != null) {
            return cashRate;
        }
        BigDecimal currentRate = implicitRate(competitor.getCurrentArs(), competitor.getCurrentUsd());
        if (currentRate != null) {
            return currentRate;
        }
        return implicitRate(competitor.getListArs(), competitor.getListUsd());
    }

    private BigDecimal percentage(int value, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(total), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal difference(BigDecimal mine, BigDecimal competitor) {
        if (!isPositive(mine) || !isPositive(competitor)) {
            return null;
        }
        return competitor.subtract(mine).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String comparisonStatus(BigDecimal difference) {
        if (difference == null) {
            return NOT_AVAILABLE;
        }
        int sign = difference.signum();
        if (sign > 0) {
            return CHEAPER;
        }
        if (sign < 0) {
            return MORE_EXPENSIVE;
        }
        return EQUAL;
    }

    private Comparator<BombaiComparativaProductResponse> defaultComparator() {
        return Comparator
                .comparingInt(this::priority)
                .thenComparing((BombaiComparativaProductResponse row) -> row.getDifferences().getUsd(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BombaiComparativaProductResponse::getBrand, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BombaiComparativaProductResponse::getModel, String.CASE_INSENSITIVE_ORDER);
    }

    private int priority(BombaiComparativaProductResponse row) {
        String overall = row.getComparison() == null ? INCOMPLETE : row.getComparison().getOverall();
        String usd = row.getComparison() == null ? NOT_AVAILABLE : row.getComparison().getUsd();
        if (CHEAPER_ALL.equals(overall)) {
            return 0;
        }
        if (CHEAPER.equals(usd)) {
            return 1;
        }
        if (MIXED.equals(overall)) {
            return 2;
        }
        if (EQUAL.equals(usd) || COMPETITIVE.equals(overall)) {
            return 3;
        }
        if (MORE_EXPENSIVE.equals(usd) || MORE_EXPENSIVE_ALL.equals(overall)) {
            return 4;
        }
        return 5;
    }

    private String matchKey(String brand, String model) {
        return TiendaPorteTextUtils.normalize(brand) + "|" + TiendaPorteTextUtils.normalize(model);
    }

    private int colorCount(List<TiendaPorteColorStockResponse> colors) {
        if (colors == null || colors.isEmpty()) {
            return 0;
        }
        return (int) colors.stream()
                .filter(color -> color != null && !isBlank(color.getColor()))
                .count();
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (isPositive(value)) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        return isPositive(value) ? value.setScale(MONEY_SCALE, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal money(BigDecimal value) {
        return isPositive(value) ? value.setScale(MONEY_SCALE, RoundingMode.HALF_UP) : null;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String safeText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MineProduct(
            String brand,
            String model,
            String origin,
            Integer colorCount,
            BombaiComparativaMinePricesResponse mine
    ) {
    }

    private record MatchProjection(
            String status,
            String message,
            BombaiComparativaCompetitorPricesResponse competitor
    ) {

        private static MatchProjection empty(String status, String message) {
            return new MatchProjection(status, message, new BombaiComparativaCompetitorPricesResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
    }

    private record ComparisonValue(
            String status,
            BigDecimal mine,
            BigDecimal competitor,
            BigDecimal difference
    ) {
    }
}
