import { of } from 'rxjs';

import { AdminConfigService } from '../services/admin-config.service';
import { ProductContentAdminComponent } from './product-content-admin.component';

describe('ProductContentAdminComponent Porte bulk publishing', () => {
  let service: jasmine.SpyObj<AdminConfigService>;
  let component: ProductContentAdminComponent;

  beforeEach(() => {
    service = jasmine.createSpyObj<AdminConfigService>('AdminConfigService', [
      'getPorteBulkPublishPreview',
      'publishPorteProductContents',
      'getProductContents'
    ]);
    service.getProductContents.and.returnValue(of({
      data: [{
        seoCatalogProductId: 1,
        marca: 'XIAOMI',
        modelo: 'NOTE',
        slug: 'xiaomi-note',
        active: true,
        status: 'PUBLISHED',
        imageCount: 1,
        updatedAt: null,
        hasUnpublishedChanges: false
      }],
      brands: ['XIAOMI'],
      page: 1,
      limit: 25,
      total: 1,
      totalPages: 1,
      hasNext: false
    }));
    component = new ProductContentAdminComponent(service);
  });

  afterEach(() => component.ngOnDestroy());

  it('confirms the preview, publishes once and reloads the product states', () => {
    service.getPorteBulkPublishPreview.and.returnValue(of({
      eligibleToPublish: 2,
      alreadyPublished: 1,
      withoutValidPorteSheet: 1,
      inactiveProducts: 0,
      skippedOtherReasons: 0
    }));
    service.publishPorteProductContents.and.returnValue(of({
      totalEvaluated: 4,
      published: 2,
      skipped: 2,
      errors: 0,
      errorDetails: []
    }));

    component.openPortePublishPreview();
    expect(component.publishModalOpen).toBeTrue();
    expect(component.publishPreview?.eligibleToPublish).toBe(2);

    component.publishPorteSheets();

    expect(service.publishPorteProductContents).toHaveBeenCalledTimes(1);
    expect(service.getProductContents).toHaveBeenCalledTimes(1);
    expect(component.bulkPublishResult?.published).toBe(2);
    expect(component.products[0].status).toBe('PUBLISHED');
    expect(component.publishModalOpen).toBeFalse();
  });

  it('interprets epoch seconds and milliseconds in Argentina time', () => {
    expect(component.formatDate(1_700_000_000)).toBe('14/11/2023, 19:13:20');
    expect(component.formatDate(1_700_000_000_000)).toBe('14/11/2023, 19:13:20');
  });
});
