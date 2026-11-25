package celulares.cordobacelulares.integration.liberadosya.service;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaQuotationResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaQuotationResponse.MarkupByTransferScheme;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LiberadosYaQuotationServiceTest {

    private static final String CURRENT_APP_JS = """
            const exchange = 1700;
            const settlement = 1 - 0.03;
            const options = [
              {name:'Efectivo en pesos', kind:'cash', count:1},
              {name:'Dólar billete', kind:'dollar', count:1},
              {name:'Transferencia en pesos', kind:'transfer', count:1},
              {name:'USDT criptomoneda', kind:'usdt', count:1},
              {name:'Tarjeta · 1 pago', count:1, net:.93, base:'transfer'},
              {name:'QR · 1 pago', count:1, net:.97*settlement},
              {name:'Plan Z · 3 pagos', count:3, net:.89*settlement, base:'transfer'},
              {name:'Tarjeta · 3 cuotas', count:3, net:.88*settlement, base:'transfer'},
              {name:'Tarjeta · 6 cuotas', count:6, net:.82*settlement, base:'transfer'}
            ];
            function quote(index,p,color){
              const mode=options[index];
              const active=currentCash(p,color);
              const cashArs=p.currency==='USD'?active*exchange:active;
              const rounding=(p.net7Pricing||p.net10Pricing)?Math.ceil:Math.round;
              const transferArs=Math.ceil(cashArs*100/98);
              const total=mode.net
                ?rounding((mode.base==='transfer'?transferArs:cashArs)/mode.net)
                :mode.kind==='dollar'?rounding(cashArs/exchange)
                :mode.kind==='usdt'?rounding(transferArs/exchange)
                :mode.kind==='transfer'?transferArs:cashArs;
              return total;
            }
            """;

    private final AtomicReference<String> appJs = new AtomicReference<>(CURRENT_APP_JS);
    private HttpServer server;
    private LiberadosYaQuotationService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> respond(exchange, "text/html", "<script src='/app.js'></script>"));
        server.createContext("/app.js", exchange -> respond(exchange, "application/javascript", appJs.get()));
        server.start();

        LiberadosYaProperties properties = new LiberadosYaProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setConnectTimeoutSeconds(2);
        properties.setReadTimeoutSeconds(2);
        service = new LiberadosYaQuotationService(properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void extractsCurrentFormulaAndNominalMarkups() {
        LiberadosYaQuotationResponse response = service.getQuotations();

        assertThat(response.updateStatus()).isEqualTo(LiberadosYaQuotationResponse.UpdateStatus.CURRENT);
        assertThat(response.dolarBillete()).isEqualByComparingTo("1700");
        assertMarkup(response.transferencia(), "2.0408");
        assertMarkup(response.tarjeta1Pago(), "9.7213");
        assertMarkup(response.qr1Pago(), "6.2812");
        assertMarkup(response.planZ3Pagos(), "18.1986");
        assertMarkup(response.tarjeta3Pagos(), "19.5417");
        assertMarkup(response.tarjeta6Pagos(), "28.2887");
    }

    @Test
    void rejectsUnrecognizedTransferFactors() {
        appJs.set(CURRENT_APP_JS.replace("cashArs*100/98", "cashArs*101/98"));

        LiberadosYaQuotationResponse response = service.getQuotations();

        assertThat(response.updateStatus()).isEqualTo(LiberadosYaQuotationResponse.UpdateStatus.UNAVAILABLE);
        assertThat(response.errorMessage()).contains("Factores no soportados");
    }

    @Test
    void appliesMathRoundAndMathCeilToConcreteAmounts() {
        BigDecimal cashArs = new BigDecimal("980");
        BigDecimal settlement = new BigDecimal("0.97");
        BigDecimal transferArs = cashArs.multiply(new BigDecimal("100"))
                .divide(new BigDecimal("98"), MathContext.DECIMAL128)
                .setScale(0, RoundingMode.CEILING);

        assertThat(transferArs).isEqualByComparingTo("1000");
        assertRoundedTotals(transferArs, new BigDecimal("0.93"), 1075, 1076);
        assertRoundedTotals(cashArs, new BigDecimal("0.97").multiply(settlement), 1042, 1042);
        assertRoundedTotals(transferArs, new BigDecimal("0.89").multiply(settlement), 1158, 1159);
        assertRoundedTotals(transferArs, new BigDecimal("0.88").multiply(settlement), 1172, 1172);
        assertRoundedTotals(transferArs, new BigDecimal("0.82").multiply(settlement), 1257, 1258);
    }

    private static void assertMarkup(MarkupByTransferScheme markup, String expected) {
        assertThat(markup.general()).isEqualByComparingTo(expected);
        assertThat(markup.net7Net10()).isEqualByComparingTo(expected);
    }

    private static void assertRoundedTotals(
            BigDecimal base,
            BigDecimal net,
            long expectedMathRound,
            long expectedMathCeil
    ) {
        BigDecimal total = base.divide(net, MathContext.DECIMAL128);
        assertThat(total.setScale(0, RoundingMode.HALF_UP).longValueExact()).isEqualTo(expectedMathRound);
        assertThat(total.setScale(0, RoundingMode.CEILING).longValueExact()).isEqualTo(expectedMathCeil);
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
