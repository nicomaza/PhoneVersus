import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ComparativaResponse, LiberadosYaCatalogStatus } from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';
import { ComparativaComponent } from './comparativa.component';

describe('ComparativaComponent', () => {
  let fixture: ComponentFixture<ComparativaComponent>;

  const response: ComparativaResponse = {
    summary: {
      competitorTransferMarkup: { average: null, median: null, min: null, max: null, count: 0 },
      competitorCardMarkup: { average: null, median: null, min: null, max: null, count: 0 },
      competitorImplicitRateOnMyCost: { average: 1750, median: 1700, min: 1600, max: 1800, count: 1 },
      competitorCustomerExchangeRate: { average: null, median: null, min: null, max: null, count: 0 },
      totalProducts: 1,
      matchedProducts: 1,
      comparableProducts: 1,
      mineCheaperCount: 1,
      mineCheaperPercent: 100,
      mineMoreExpensiveCount: 0,
      mineMoreExpensivePercent: 0,
      mixedCount: 0,
      withoutMatchCount: 0,
      resultStatus: 'CHEAPER_ALL'
    },
    products: [{
      brand: 'Samsung',
      model: 'Galaxy S24 256 GB 5G',
      fullName: 'Samsung Galaxy S24 256 GB 5G',
      origin: 'TIENDA_PORTE',
      colorCount: 2,
      matchStatus: 'MATCHED',
      matchMessage: null,
      mine: { costUsd: 500, priceUsd: 500, cashArs: 800000, transferArs: null, cardArs: null },
      competitor: {
        matched: true,
        name: 'Samsung Galaxy S24 256GB 5G',
        cashArs: 850000,
        currentArs: null,
        listArs: null,
        transferArs: null,
        cardArs: null,
        cashUsd: null,
        currentUsd: null,
        listUsd: null,
        mainUsd: 510
      },
      differences: { cashArs: 50000, transferArs: null, cardArs: null, usd: 10 },
      comparison: {
        cash: 'CHEAPER', transfer: 'NOT_AVAILABLE', card: 'NOT_AVAILABLE', usd: 'CHEAPER',
        overall: 'CHEAPER_ALL', comparableCount: 2
      }
    }]
  };

  const status: LiberadosYaCatalogStatus = {
    available: true,
    refreshing: false,
    phase: 'IDLE',
    processed_products: 1,
    total_products: 1,
    percent: 100,
    current_product: null,
    generated_at: new Date().toISOString(),
    total_urls_found: 1,
    errors_count: 0,
    last_successful_refresh_at: new Date().toISOString(),
    last_attempt_at: new Date().toISOString(),
    last_error: null
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparativaComponent],
      providers: [{
        provide: AdminConfigService,
        useValue: {
          getComparativa: () => of(response),
          getLiberadosYaCatalogStatus: () => of(status)
        }
      }]
    }).compileComponents();

    fixture = TestBed.createComponent(ComparativaComponent);
    fixture.detectChanges();
  });

  it('renders the compact four-column cash comparison', () => {
    const headers = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.comparison-table thead th')
    ).map(element => element.textContent?.trim());

    expect(headers).toEqual([
      'Marca y modelo',
      'Mi precio en pesos',
      'Precio en pesos competencia',
      'Resultado comparativa'
    ]);
    expect(fixture.nativeElement.textContent).toContain('Yo más barato por');
    expect(fixture.nativeElement.textContent).not.toContain('Transferencia sobre efectivo');
    expect(fixture.nativeElement.textContent).not.toContain('Tarjeta sobre efectivo');
  });

  it('shows only the requested backend USD and difference values in the information table', () => {
    const labels = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.popover-card th')
    ).map(element => element.textContent?.trim());

    expect(labels).toEqual([
      'Precio competencia en dólares',
      'Mi costo en dólares',
      'Diferencia en dólares',
      'Diferencia en pesos'
    ]);
    expect(fixture.nativeElement.textContent).toContain('USD 510');
    expect(fixture.nativeElement.textContent).toContain('USD 500');
    expect(fixture.nativeElement.textContent).toContain('+USD 10');
  });
});
