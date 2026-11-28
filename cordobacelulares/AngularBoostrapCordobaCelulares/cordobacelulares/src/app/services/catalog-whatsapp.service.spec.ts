import { TestBed } from '@angular/core/testing';

import { CatalogWhatsappService } from './catalog-whatsapp.service';

describe('CatalogWhatsappService', () => {
  let service: CatalogWhatsappService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CatalogWhatsappService);
  });

  it('includes the selected financing total, installments and available colors', () => {
    const message = service.buildProductMessage({
      marca: 'SAMSUNG',
      modelo: 'Galaxy de prueba',
      precioPesos: 500000,
      precioTransferenciaBancaria: 520000,
      precioTarjeta6Pagos: 666666,
      colores: ['Negro', 'Azul']
    }, {
      label: 'Tarjeta 6 cuotas',
      total: 666666,
      installments: 6,
      installmentAmount: 111111
    });

    expect(message).toContain('SAMSUNG Galaxy de prueba');
    expect(message).toContain('- Efectivo: $ 500.000');
    expect(message).toContain('- Transferencia: $ 520.000');
    expect(message).toContain('- Tarjeta 6 cuotas: $ 666.666 (6 cuotas de $ 111.111)');
    expect(message).toContain('Colores: Negro, Azul');
  });

  it('does not add installments to one-payment financing', () => {
    const message = service.buildProductMessage({
      marca: 'APPLE',
      modelo: 'iPhone de prueba',
      precioPesos: 700000,
      precioTransferenciaBancaria: 720000
    }, {
      label: 'Tarjeta 1 pago',
      total: 750000
    });

    expect(message).toContain('- Tarjeta 1 pago: $ 750.000');
    expect(message).not.toContain('cuotas de');
  });

  it('sends only the perfume name and transfer price', () => {
    const message = service.buildProductMessage({
      marca: ' Perfúmes ',
      modelo: 'Fragancia floral',
      precioPesos: 100000,
      precioTransferenciaBancaria: 103000,
      precioTarjeta6Pagos: 130000,
      colores: ['Rosa']
    }, {
      label: 'Tarjeta 6 cuotas',
      total: 130000,
      installments: 6,
      installmentAmount: 21666.67
    });

    expect(message).toContain('Perfúmes Fragancia floral');
    expect(message).toContain('- Transferencia: $ 103.000');
    expect(message).not.toContain('Efectivo');
    expect(message).not.toContain('Tarjeta');
    expect(message).not.toContain('cuotas');
    expect(message).not.toContain('Colores');
  });
});
