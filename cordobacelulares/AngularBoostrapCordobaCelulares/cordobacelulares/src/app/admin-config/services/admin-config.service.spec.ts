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
});
