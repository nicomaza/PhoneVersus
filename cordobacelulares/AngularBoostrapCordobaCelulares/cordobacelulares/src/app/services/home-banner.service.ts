import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface PublicHomeBanner {
  title: string;
  altText: string;
  linkUrl: string | null;
  desktopImageUrl: string;
  mobileImageUrl: string;
}

export interface AdminHomeBanner extends PublicHomeBanner {
  id: number;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface HomeBannerMetadata {
  title: string;
  altText: string;
  linkUrl: string | null;
}

@Injectable({ providedIn: 'root' })
export class HomeBannerService {
  private readonly publicUrl = '/api/public/home-banners';
  private readonly adminUrl = '/api/admin/home-banners';

  constructor(private http: HttpClient) { }

  listPublic(): Observable<PublicHomeBanner[]> {
    return this.http.get<PublicHomeBanner[]>(this.publicUrl);
  }

  listAdmin(): Observable<AdminHomeBanner[]> {
    return this.http.get<AdminHomeBanner[]>(this.adminUrl);
  }

  create(metadata: HomeBannerMetadata, desktopImage: File, mobileImage?: File): Observable<AdminHomeBanner> {
    const form = this.creationForm(metadata, desktopImage, mobileImage);
    return this.http.post<AdminHomeBanner>(this.adminUrl, form);
  }

  updateMetadata(id: number, metadata: HomeBannerMetadata): Observable<AdminHomeBanner> {
    return this.http.put<AdminHomeBanner>(`${this.adminUrl}/${id}`, metadata);
  }

  setActive(id: number, active: boolean): Observable<AdminHomeBanner> {
    return this.http.patch<AdminHomeBanner>(`${this.adminUrl}/${id}/active`, { active });
  }

  reorder(bannerIds: number[]): Observable<AdminHomeBanner[]> {
    return this.http.put<AdminHomeBanner[]>(`${this.adminUrl}/order`, { bannerIds });
  }

  replaceDesktop(id: number, image: File): Observable<AdminHomeBanner> {
    return this.replaceImage(id, 'desktop', image);
  }

  replaceMobile(id: number, image: File): Observable<AdminHomeBanner> {
    return this.replaceImage(id, 'mobile', image);
  }

  removeMobile(id: number): Observable<AdminHomeBanner> {
    return this.http.delete<AdminHomeBanner>(`${this.adminUrl}/${id}/images/mobile`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/${id}`);
  }

  private creationForm(metadata: HomeBannerMetadata, desktopImage: File, mobileImage?: File): FormData {
    const form = new FormData();
    form.append('title', metadata.title);
    form.append('altText', metadata.altText);
    if (metadata.linkUrl) {
      form.append('linkUrl', metadata.linkUrl);
    }
    form.append('desktopImage', desktopImage);
    if (mobileImage) {
      form.append('mobileImage', mobileImage);
    }
    return form;
  }

  private replaceImage(id: number, target: 'desktop' | 'mobile', image: File): Observable<AdminHomeBanner> {
    const form = new FormData();
    form.append('image', image);
    return this.http.put<AdminHomeBanner>(`${this.adminUrl}/${id}/images/${target}`, form);
  }
}
