import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface CatalogCacheStatusResponse {
  initialized: boolean;
  fresh: boolean;
  stale: boolean;
  refreshing: boolean;
  version: number;
  productCount: number;
  lastSuccessfulRefreshAt?: string | null;
  lastAttemptAt?: string | null;
  expiresAt?: string | null;
  nextAllowedRefreshAt?: string | null;
  lastError?: string | null;
}

export interface CatalogCacheRefreshResponse {
  refreshStarted: boolean;
  alreadyRunning: boolean;
  blockedByCooldown: boolean;
  status: CatalogCacheStatusResponse;
}

@Injectable({
  providedIn: 'root'
})
export class CatalogCacheService {
  private readonly API_URL = 'http://localhost:8080/api/admin/catalogo/cache';

  constructor(private http: HttpClient) { }

  getStatus(): Observable<CatalogCacheStatusResponse> {
    return this.http.get<CatalogCacheStatusResponse>(`${this.API_URL}/status`);
  }

  refresh(): Observable<CatalogCacheRefreshResponse> {
    return this.http.post<CatalogCacheRefreshResponse>(`${this.API_URL}/refresh`, {});
  }
}
