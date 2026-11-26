import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdminConfigService } from './admin-config.service';

describe('AdminConfigService Porte bulk publishing', () => {
  let service: AdminConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AdminConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the backend preview without using the paginated product list', () => {
    service.getPorteBulkPublishPreview().subscribe();

    const request = httpMock.expectOne('/api/admin/product-content/porte/publish/preview');
    expect(request.request.method).toBe('GET');
    request.flush({
      eligibleToPublish: 3,
      alreadyPublished: 1,
      withoutValidPorteSheet: 2,
      inactiveProducts: 1,
      skippedOtherReasons: 0
    });
  });

  it('publishes the complete batch with one backend request', () => {
    service.publishPorteProductContents().subscribe();

    const request = httpMock.expectOne('/api/admin/product-content/porte/publish');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ totalEvaluated: 5, published: 3, skipped: 2, errors: 0, errorDetails: [] });
  });

  it('requests the LiberadosYa catalog status used by the comparison view', () => {
    service.getLiberadosYaCatalogStatus().subscribe();

    const request = httpMock.expectOne('/api/admin/integrations/liberadosya/status');
    expect(request.request.method).toBe('GET');
    request.flush({
      available: true,
      refreshing: false,
      phase: 'IDLE',
      processed_products: 10,
      total_products: 10,
      percent: 100,
      current_product: null,
      generated_at: '2026-11-26T12:00:00Z',
      total_urls_found: 10,
      errors_count: 0,
      last_successful_refresh_at: '2026-11-26T12:00:00Z',
      last_attempt_at: '2026-11-26T12:00:00Z',
      last_error: null
    });
  });
});
