package celulares.cordobacelulares.integration.liberadosya.scraping;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiberadosYaCatalogScriptParserTest {

    private static final String PRICING = """
            'use strict';
            function rankedPrices(product,color){return [currentCash(product,color)];}
            function currentCash(product,color){return product.colorPrices?.[color]??product.cash;}
            """;

    private static final String APP = """
            const exchange = 1700;
            const storefrontPhoneTitles={"0":"Xiaomi Redmi Note 14 Pro 5G 256GB 8RAM"};
            const products = window.catalogo.map(([category,sourceName,cash,currency],i)=>({
              id:i,category,sourceName,name:sourceName,cash,currency,
              ...window.fichas[i],
              ...(window.fichas[i]?.retiredFromStorefront===true?{cash:null}:{}),
              ...(storefrontPhoneTitles[i]?{name:storefrontPhoneTitles[i],previousDisplayName:sourceName}:{})
            }));
            const hasPublicPrice=product=>Number.isFinite(product.cash)&&product.cash>0;
            function quote(p,index,color) {
              const mode=options[index];
              const active=currentCash(p,color);const cashArs=p.currency==='USD'?active*exchange:active;
              const rounding=(p.net7Pricing||p.net10Pricing)?Math.ceil:Math.round;
              const transferArs=Math.ceil(cashArs*100/98);
              const total=mode.net?rounding((mode.base==='transfer'?transferArs:cashArs)/mode.net):mode.kind==='dollar'?rounding(cashArs/exchange):mode.kind==='usdt'?rounding(transferArs/exchange):mode.kind==='transfer'?transferArs:cashArs;
              return total;
            }
            """;

    private final LiberadosYaCatalogScriptParser parser = new LiberadosYaCatalogScriptParser(new ObjectMapper());

    @Test
    void extractsPublishedProductsTitlesVariantsAndRealArsUsdQuotesFromCents() {
        String catalog = """
                window.catalogo = [
                  ["Xiaomi","Xiaomi REDMI NOTE 14 PRO 5G 8GB 256GB",17000000,"ARS"],
                  ["Samsung","Samsung Galaxy A55 5G 8GB 256GB",20000000,"ARS"],
                  ["Motorola","Motorola Edge 50 5G 8GB 256GB",null,"ARS"],
                  ["iPhone","iPhone 16 128GB",10000,"USD"]
                ];
                """;
        String fichas = """
                window.fichas = [
                  {
                    "attributes":[["Memoria RAM física","8 GB"],["Almacenamiento","256 GB"]],
                    "colors":["Black","Blue"],
                    "colorPrices":{"Black":17000000,"Blue":18000000},
                    "image":"/images/redmi.webp"
                  },
                  {"retiredFromStorefront":true},
                  {"colorPrices":{"Green":30000000}},
                  {"colors":["Black"]}
                ];
                """;

        LiberadosYaCatalogScriptParser.ParsedCatalog result = parse(catalog, fichas, APP);

        assertThat(result.sourceProducts()).isEqualTo(4);
        assertThat(result.retiredProducts()).isEqualTo(1);
        assertThat(result.unpublishedProducts()).isEqualTo(1);
        assertThat(result.products()).hasSize(2);

        LiberadosYaProduct xiaomi = result.products().get(0);
        assertThat(xiaomi.getNombre()).isEqualTo("Xiaomi Redmi Note 14 Pro 5G 256GB 8RAM");
        assertThat(xiaomi.getPrecios().getArs().getEfectivo()).isEqualByComparingTo("170000.00");
        assertThat(xiaomi.getPrecios().getArs().getActual()).isEqualByComparingTo("173469.39");
        assertThat(xiaomi.getPrecios().getUsd().getEfectivo()).isEqualByComparingTo("100.00");
        assertThat(xiaomi.getPreciosPorVariante()).containsOnlyKeys("Black", "Blue");
        assertThat(xiaomi.getPreciosPorVariante().get("Blue").getArs().getEfectivo())
                .isEqualByComparingTo("180000.00");
        assertThat(xiaomi.getPreciosPorVariante().get("Blue").getUsd().getEfectivo())
                .isEqualByComparingTo("105.88");
        assertThat(xiaomi.getEspecificaciones().get("Ficha tecnica"))
                .containsEntry("Memoria RAM física", "8 GB");

        LiberadosYaProduct iphone = result.products().get(1);
        assertThat(iphone.getPrecios().getArs().getEfectivo()).isEqualByComparingTo("170000.00");
        assertThat(iphone.getPrecios().getUsd().getEfectivo()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsUnrecognizedPricingFormula() {
        assertThatThrownBy(() -> parser.parse(
                URI.create("https://example.test/"),
                URI.create("https://example.test/catalogo.js?v=1"),
                "window.catalogo=[[\"Xiaomi\",\"Modelo\",10000,\"ARS\"]];",
                URI.create("https://example.test/fichas.js?v=2"),
                "window.fichas=[{}];",
                URI.create("https://example.test/pricing.js?v=3"),
                "function currentCash(product,color){return product.cash;}",
                URI.create("https://example.test/app.js?v=4"),
                APP,
                Instant.parse("2026-01-01T00:00:00Z")
        )).isInstanceOf(LiberadosYaIntegrationException.class)
                .hasMessageContaining("currentCash");
    }

    @Test
    void discoversVersionedAssetsFromHtmlWithoutHardcodingHashes() {
        LiberadosYaProperties properties = new LiberadosYaProperties();
        properties.setRequestDelayMs(0);
        LiberadosYaScrapingClient client = new LiberadosYaScrapingClient(
                properties,
                parser,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                HttpClient.newHttpClient()
        );
        String html = """
                <script src="catalogo.js?v=cat-123"></script>
                <script src="/assets/fichas.js?v=fichas-456"></script>
                <script src="pricing.js?v=pricing-789"></script>
                <script src="app.js?v=app-abc"></script>
                """;

        Map<LiberadosYaScrapingClient.Asset, URI> assets = client.locateAssets(URI.create("https://example.test/"), html);

        assertThat(assets.get(LiberadosYaScrapingClient.Asset.CATALOG).toString())
                .isEqualTo("https://example.test/catalogo.js?v=cat-123");
        assertThat(assets.get(LiberadosYaScrapingClient.Asset.FICHAS).toString())
                .isEqualTo("https://example.test/assets/fichas.js?v=fichas-456");
        assertThat(assets.get(LiberadosYaScrapingClient.Asset.PRICING).toString())
                .isEqualTo("https://example.test/pricing.js?v=pricing-789");
        assertThat(assets.get(LiberadosYaScrapingClient.Asset.APP).toString())
                .isEqualTo("https://example.test/app.js?v=app-abc");
    }

    private LiberadosYaCatalogScriptParser.ParsedCatalog parse(String catalog, String fichas, String app) {
        return parser.parse(
                URI.create("https://example.test/"),
                URI.create("https://example.test/catalogo.js?v=1"), catalog,
                URI.create("https://example.test/fichas.js?v=2"), fichas,
                URI.create("https://example.test/pricing.js?v=3"), PRICING,
                URI.create("https://example.test/app.js?v=4"), app,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
