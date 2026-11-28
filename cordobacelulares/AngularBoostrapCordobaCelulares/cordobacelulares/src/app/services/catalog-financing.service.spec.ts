import { TestBed } from '@angular/core/testing';
import { CatalogFinancingService, CatalogPaymentProfile } from './catalog-financing.service';

describe('CatalogFinancingService combined payments', () => {
  let service: CatalogFinancingService;
  const profile: CatalogPaymentProfile = {
    transferMultiplier: 1.02,
    financingMultipliers: { tarjeta1: 1.1, tarjeta6: 1.28 }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CatalogFinancingService);
  });

  it('applies financing only to the selected card base and transfer only to the balance', () => {
    const result = service.simulateCombined(500000, 200000, 'transfer', profile);

    expect(result?.balanceBase).toBe(300000);
    expect(result?.balanceTotal).toBe(306000);
    expect(result?.financedBase).toBe(200000);
    expect(result?.financing.find(option => option.key === 'tarjeta6')?.total).toBe(256000);
    expect(result?.financing.find(option => option.key === 'tarjeta6')?.finalTotal).toBe(562000);
  });

  it('rejects non-positive amounts and a financed base greater than the total', () => {
    expect(service.simulateCombined(500000, 500001, 'cash', profile)).toBeNull();
    expect(service.simulateCombined(500000, 0, 'cash', profile)).toBeNull();
  });
});
