import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import Swal from 'sweetalert2';
import {
  AdminCatalogPageResponse,
  AdminCatalogProductRow,
  BlockedCatalogProductResponse
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';
import { TiendaPorteProductsAdminComponent } from './tienda-porte-products-admin.component';

describe('TiendaPorteProductsAdminComponent', () => {
  let service: jasmine.SpyObj<AdminConfigService>;
  let fixture: ComponentFixture<TiendaPorteProductsAdminComponent>;
  let component: TiendaPorteProductsAdminComponent;

  beforeEach(async () => {
    service = jasmine.createSpyObj<AdminConfigService>('AdminConfigService', [
      'getAdminCatalogProducts',
      'getBlockedCatalogProducts',
      'blockCatalogProduct',
      'unblockCatalogProduct'
    ]);
    service.getAdminCatalogProducts.and.returnValue(of(pageResponse([catalogRow()])));
    service.getBlockedCatalogProducts.and.returnValue(of([]));
    service.blockCatalogProduct.and.returnValue(of(blockedProduct()));
    service.unblockCatalogProduct.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [TiendaPorteProductsAdminComponent],
      providers: [
        { provide: AdminConfigService, useValue: service }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TiendaPorteProductsAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('shows only the requested general columns', () => {
    const headers = headerTexts();

    expect(headers).toEqual(['ID', 'Marca', 'Modelo', 'Color', 'Stock', 'USD', 'Pesos', 'Origen', 'Acciones']);
    expect(headers).not.toContain('Transferencia');
    expect(headers).not.toContain('3 pagos');
    expect(headers).not.toContain('6 pagos');
    expect(headers).not.toContain('12 pagos');
  });

  it('builds payment tooltip details with safe placeholders', () => {
    const row = catalogRow({
      precioTransferenciaBancaria: 120000,
      precioTarjeta3Pagos: 150000,
      precioTarjeta6Pagos: null,
      precioTarjeta12Pagos: Number.NaN
    });

    const lines = component.paymentTooltipLines(row);

    expect(lines.map(line => line.label)).toEqual(['Transferencia', '3 cuotas', '6 cuotas', '12 cuotas']);
    expect(lines.map(line => line.value)).toContain('—');
  });

  it('disables blocking for rows without external provider id', () => {
    const row = catalogRow({ id: null });

    expect(component.canBlock(row)).toBeFalse();
    expect(component.blockButtonTitle(row)).toContain('no informo un ID');
  });

  it('shows blocked products in the segmented blocked view', () => {
    service.getBlockedCatalogProducts.and.returnValue(of([blockedProduct()]));

    component.setActiveView('blocked');
    fixture.detectChanges();

    expect(headerTexts()).toEqual(['ID', 'Marca', 'Modelo', 'Precio USD', 'Proveedor', 'Acciones']);
    expect(fixture.nativeElement.textContent).toContain('SAMSUNG A56');
    expect(fixture.nativeElement.textContent).toContain('Tienda Porte');
  });

  it('confirms and sends block request for a valid product id', fakeAsync(() => {
    spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));

    component.block(catalogRow({ id: 123, modelo: 'SAMSUNG A56' }));
    tick();
    fixture.detectChanges();

    expect(service.blockCatalogProduct).toHaveBeenCalledWith(123);
    expect(service.getBlockedCatalogProducts).toHaveBeenCalled();
    expect(service.getAdminCatalogProducts).toHaveBeenCalled();
  }));

  function headerTexts(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('thead:first-of-type th'))
      .map(header => (header as HTMLElement).textContent?.trim().replace(/\s+/g, ' ') ?? '');
  }

  function pageResponse(data: AdminCatalogProductRow[]): AdminCatalogPageResponse {
    return {
      page: 1,
      limit: 50,
      total: data.length,
      totalPages: 1,
      hasNext: false,
      data
    };
  }

  function catalogRow(overrides: Partial<AdminCatalogProductRow> = {}): AdminCatalogProductRow {
    return {
      id: 123,
      marca: 'SAMSUNG',
      modelo: 'SAMSUNG A56',
      color: 'Black',
      cantidad: 2,
      origen: 'TIENDA_PORTE',
      precioUsd: 320,
      precioPesos: 320000,
      precioTransferenciaBancaria: 352000,
      precioTarjeta3Pagos: 384000,
      precioTarjeta6Pagos: 416000,
      precioTarjeta12Pagos: 448000,
      ...overrides
    };
  }

  function blockedProduct(overrides: Partial<BlockedCatalogProductResponse> = {}): BlockedCatalogProductResponse {
    return {
      id: 123,
      marca: 'SAMSUNG',
      modelo: 'SAMSUNG A56',
      precioUsd: 320,
      origen: 'TIENDA_PORTE',
      blockedAt: '2026-07-12T12:00:00Z',
      ...overrides
    };
  }
});
