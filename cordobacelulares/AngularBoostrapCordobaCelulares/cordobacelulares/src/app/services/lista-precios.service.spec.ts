import { TestBed } from '@angular/core/testing';

import { ListaPreciosService } from './lista-precios.service';

describe('ListaPreciosService', () => {
  let service: ListaPreciosService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ListaPreciosService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
