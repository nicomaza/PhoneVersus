import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NuevosPreciosService, TiendaPorteBrandResponse } from './nuevos-precios.service';

describe('NuevosPreciosService catalog variants', () => {
  let service: NuevosPreciosService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(NuevosPreciosService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('keeps different colors and selects the cheapest exact-color duplicate', () => {
    let result: TiendaPorteBrandResponse[] = [];
    service.getCatalogoPrincipal().subscribe(data => result = data);

    httpMock.expectOne('https://www.cordobacelulares.com/api/tiendaporte/equipos/categorias-principales')
      .flush([{
        marca: 'IPHONE',
        modelos: [
          variant('Cosmic Orange', 1260, 1260000),
          variant('Deep Blue', 1245, 1245000),
          variant('Cosmic Orange', 1240, 1240000),
          variant('Silver', 1250, 1250000)
        ]
      }]);

    const variants = result[0].modelos ?? [];
    const byColor = new Map(variants.map(model => [model.colores?.[0]?.color, model]));

    expect(variants.length).toBe(3);
    expect(Array.from(byColor.keys())).toEqual(jasmine.arrayWithExactContents([
      'Cosmic Orange',
      'Deep Blue',
      'Silver'
    ]));
    expect(byColor.get('Cosmic Orange')?.precioUsd).toBe(1240);
    expect(byColor.get('Deep Blue')?.precioUsd).toBe(1245);
    expect(byColor.get('Silver')?.precioUsd).toBe(1250);
    expect(variants.every(model => model.showColorOnCard)).toBeTrue();
  });

  it('groups every color when model, storage and price are the same', () => {
    let result: TiendaPorteBrandResponse[] = [];
    service.getCatalogoPrincipal().subscribe(data => result = data);

    httpMock.expectOne('https://www.cordobacelulares.com/api/tiendaporte/equipos/categorias-principales')
      .flush([{
        marca: 'IPHONE',
        modelos: [{
          modeloNombre: 'IPHONE 17 PRO MAX 512GB',
          colores: [
            { color: 'Cosmic Orange', stock: 2 },
            { color: 'Deep Blue', stock: 1 }
          ],
          precioUsd: 2547.90,
          precioPesos: 2547900
        }]
      }]);

    const models = result[0].modelos ?? [];

    expect(models.length).toBe(1);
    expect(models[0].showColorOnCard).toBeFalse();
    expect(models[0].colores?.map(color => color.color))
      .toEqual(jasmine.arrayWithExactContents(['Cosmic Orange', 'Deep Blue']));
  });

  function variant(color: string, precioUsd: number, precioPesos: number) {
    return {
      modeloNombre: 'IPHONE 17 PRO MAX 256GB',
      colores: [{ color, stock: 1 }],
      precioUsd,
      precioPesos
    };
  }
});
