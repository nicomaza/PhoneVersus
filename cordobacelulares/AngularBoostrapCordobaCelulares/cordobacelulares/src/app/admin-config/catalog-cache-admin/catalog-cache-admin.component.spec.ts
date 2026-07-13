import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogCacheStatus, CatalogRefreshResponse } from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';
import { CatalogCacheAdminComponent } from './catalog-cache-admin.component';

describe('CatalogCacheAdminComponent', () => {
  let service: jasmine.SpyObj<AdminConfigService>;
  let component: CatalogCacheAdminComponent;

  beforeEach(() => {
    service = jasmine.createSpyObj<AdminConfigService>('AdminConfigService', [
      'getCatalogCacheStatus',
      'refreshCatalogCache'
    ]);
    component = new CatalogCacheAdminComponent(service);
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('parseApiDate parses valid ISO-8601 values', () => {
    expect(component.parseApiDate('2026-07-11T16:31:41.123Z')?.toISOString())
      .toBe('2026-07-11T16:31:41.123Z');
  });

  it('parseApiDate treats numeric Unix timestamps below threshold as seconds', () => {
    expect(component.parseApiDate(1783780301)?.getTime()).toBe(1783780301000);
  });

  it('parseApiDate treats numeric Unix timestamps at threshold as milliseconds', () => {
    expect(component.parseApiDate(1783780301000)?.getTime()).toBe(1783780301000);
  });

  it('parseApiDate supports numeric timestamps sent as strings', () => {
    expect(component.parseApiDate('1783780301')?.getTime()).toBe(1783780301000);
  });

  it('parseApiDate returns null for null', () => {
    expect(component.parseApiDate(null)).toBeNull();
  });

  it('parseApiDate returns null for undefined', () => {
    expect(component.parseApiDate(undefined)).toBeNull();
  });

  it('parseApiDate returns null for empty strings', () => {
    expect(component.parseApiDate('   ')).toBeNull();
  });

  it('parseApiDate returns null for invalid dates', () => {
    expect(component.parseApiDate('not-a-date')).toBeNull();
  });

  it('snapshotAgeLabel shows seconds under one minute', () => {
    spyOn(Date, 'now').and.returnValue(Date.parse('2026-07-11T16:32:10Z'));
    component.status = status({ lastSuccessfulRefreshAt: '2026-07-11T16:31:41Z' });

    expect(component.snapshotAgeLabel()).toBe('29s');
  });

  it('snapshotAgeLabel shows minutes and seconds under one hour', () => {
    spyOn(Date, 'now').and.returnValue(Date.parse('2026-07-11T16:36:02Z'));
    component.status = status({ lastSuccessfulRefreshAt: '2026-07-11T16:31:41Z' });

    expect(component.snapshotAgeLabel()).toBe('4m 21s');
  });

  it('snapshotAgeLabel shows hours and minutes over one hour', () => {
    spyOn(Date, 'now').and.returnValue(Date.parse('2026-07-11T18:45:41Z'));
    component.status = status({ lastSuccessfulRefreshAt: '2026-07-11T16:31:41Z' });

    expect(component.snapshotAgeLabel()).toBe('2h 14m');
  });

  it('snapshotAgeLabel does not show negative values for small clock drift', () => {
    spyOn(Date, 'now').and.returnValue(Date.parse('2026-07-11T16:31:40Z'));
    component.status = status({ lastSuccessfulRefreshAt: '2026-07-11T16:31:41Z' });

    expect(component.snapshotAgeLabel()).toBe('0s');
  });

  it('snapshotAgeLabel returns a placeholder instead of huge values for invalid dates', () => {
    component.status = status({ lastSuccessfulRefreshAt: 'not-a-date' });

    expect(component.snapshotAgeLabel()).toBe('—');
  });

  it('formatDate delegates display to es-AR locale', () => {
    const localeSpy = spyOn(Date.prototype, 'toLocaleString').and.returnValue('11/7/2026, 13:31:41');

    expect(component.formatDate('2026-07-11T16:31:41.123Z')).toBe('11/7/2026, 13:31:41');
    expect(localeSpy).toHaveBeenCalledWith('es-AR');
  });

  it('polling stops when refreshing changes to false', fakeAsync(() => {
    service.refreshCatalogCache.and.returnValue(of(refreshResponse(status({ refreshing: true }))));
    service.getCatalogCacheStatus.and.returnValue(of(status({ refreshing: false, fresh: true })));

    component.refreshCatalog();
    tick(2000);
    tick(4000);

    expect(service.getCatalogCacheStatus).toHaveBeenCalledTimes(1);
    expect(component.status?.refreshing).toBeFalse();
    component.ngOnDestroy();
  }));

  it('message changes from started to success after polling finishes', fakeAsync(() => {
    service.refreshCatalogCache.and.returnValue(of(refreshResponse(status({ refreshing: true }))));
    service.getCatalogCacheStatus.and.returnValue(of(status({ refreshing: false, fresh: true, lastError: null })));

    component.refreshCatalog();
    expect(component.message).toBe('Actualizacion iniciada en segundo plano.');

    tick(2000);
    expect(component.message).toBe('Catalogo actualizado correctamente.');
    component.ngOnDestroy();
  }));

  it('singleFlightLabel reflects refreshing state', () => {
    component.status = status({ refreshing: true });
    expect(component.singleFlightLabel()).toBe('Ocupado');

    component.status = status({ refreshing: false });
    expect(component.singleFlightLabel()).toBe('Libre');
  });

  function refreshResponse(cacheStatus: CatalogCacheStatus): CatalogRefreshResponse {
    return {
      refreshStarted: true,
      alreadyRunning: false,
      blockedByCooldown: false,
      status: cacheStatus
    };
  }

  function status(overrides: Partial<CatalogCacheStatus> = {}): CatalogCacheStatus {
    return {
      initialized: true,
      fresh: true,
      stale: false,
      refreshing: false,
      version: 2,
      productCount: 412,
      lastSuccessfulRefreshAt: '2026-07-11T16:31:41.123Z',
      lastAttemptAt: '2026-07-11T16:31:41.123Z',
      expiresAt: '2026-07-11T16:34:41.123Z',
      nextAllowedRefreshAt: null,
      lastError: null,
      ...overrides
    };
  }
});
