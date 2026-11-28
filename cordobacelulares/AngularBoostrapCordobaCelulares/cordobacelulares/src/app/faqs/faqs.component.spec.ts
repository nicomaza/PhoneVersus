import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CatalogWhatsappService } from '../services/catalog-whatsapp.service';
import { NuevosPreciosService } from '../services/nuevos-precios.service';
import { FaqsComponent } from './faqs.component';

describe('FaqsComponent', () => {
  let component: FaqsComponent;
  let fixture: ComponentFixture<FaqsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FaqsComponent],
      providers: [
        provideRouter([]),
        {
          provide: NuevosPreciosService,
          useValue: {
            getCatalogoPrincipal: () => of([{
              marca: 'SAMSUNG',
              modelos: [{
                modeloNombre: 'Galaxy prueba',
                precioPesos: 100,
                precioTransferenciaBancaria: 102,
                precioTarjeta1Pago: 110,
                precioTarjeta6Pagos: 128
              }]
            }])
          }
        },
        {
          provide: CatalogWhatsappService,
          useValue: { openHomeConsultation: jasmine.createSpy('openHomeConsultation') }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(FaqsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('keeps the used-equipment and combined-payment questions independent', () => {
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('¿Reciben celulares usados o hacen canjes?');
    expect(text).toContain('no recibimos equipos usados ni realizamos canjes');
    expect(text).toContain('¿Puedo pagar una parte en efectivo o transferencia y el resto con tarjeta?');
  });

  it('calculates financing only over the selected amount and transfer over the remaining balance', () => {
    component.combinedTotalControl.setValue(500000);
    component.combinedCardControl.setValue(200000);
    component.combinedBalanceMethodControl.setValue('transfer');
    fixture.detectChanges();

    const tarjeta6 = component.combinedSimulation?.financing.find(option => option.key === 'tarjeta6');
    expect(component.combinedSimulation?.balanceTotal).toBe(306000);
    expect(component.combinedSimulation?.financedBase).toBe(200000);
    expect(tarjeta6?.total).toBe(256000);
    expect(tarjeta6?.finalTotal).toBe(562000);
  });

  it('rejects a card amount greater than the cash price', () => {
    component.combinedTotalControl.setValue(100000);
    component.combinedCardControl.setValue(100001);
    component.markCombinedCardTouched();

    expect(component.invalidCombinedCard()).toBeTrue();
    expect(component.combinedSimulation).toBeNull();
  });
});
