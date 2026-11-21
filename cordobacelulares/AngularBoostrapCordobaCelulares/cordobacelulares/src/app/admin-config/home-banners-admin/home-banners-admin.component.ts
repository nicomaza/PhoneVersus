import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { AdminHomeBanner, HomeBannerService } from '../../services/home-banner.service';

interface BannerEditor extends AdminHomeBanner {
  draftTitle: string;
  draftAltText: string;
  draftLinkUrl: string;
  busy: boolean;
}

@Component({
  selector: 'app-home-banners-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './home-banners-admin.component.html',
  styleUrl: './home-banners-admin.component.css'
})
export class HomeBannersAdminComponent implements OnInit, OnDestroy {
  banners: BannerEditor[] = [];
  loading = true;
  creating = false;
  ordering = false;
  errorMessage = '';
  successMessage = '';

  newTitle = '';
  newAltText = '';
  newLinkUrl = '';
  desktopFile: File | null = null;
  mobileFile: File | null = null;
  desktopPreview = '';
  mobilePreview = '';

  constructor(private bannerService: HomeBannerService) { }

  ngOnInit(): void {
    this.loadBanners();
  }

  ngOnDestroy(): void {
    this.revokePreview(this.desktopPreview);
    this.revokePreview(this.mobilePreview);
  }

  loadBanners(): void {
    this.loading = true;
    this.clearMessages();
    this.bannerService.listAdmin().subscribe({
      next: banners => {
        this.banners = banners.map(banner => this.toEditor(banner));
        this.loading = false;
      },
      error: error => {
        this.errorMessage = this.errorText(error);
        this.loading = false;
      }
    });
  }

  selectCreateImage(target: 'desktop' | 'mobile', event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    const validation = this.validateImage(file);
    if (validation) {
      this.errorMessage = validation;
      input.value = '';
      return;
    }
    const preview = URL.createObjectURL(file);
    if (target === 'desktop') {
      this.revokePreview(this.desktopPreview);
      this.desktopFile = file;
      this.desktopPreview = preview;
    } else {
      this.revokePreview(this.mobilePreview);
      this.mobileFile = file;
      this.mobilePreview = preview;
    }
  }

  createBanner(form: NgForm): void {
    this.clearMessages();
    if (form.invalid || !this.desktopFile) {
      this.errorMessage = 'Completá el título, el texto alternativo y la imagen desktop.';
      return;
    }
    this.creating = true;
    this.bannerService.create({
      title: this.newTitle.trim(),
      altText: this.newAltText.trim(),
      linkUrl: this.cleanOptional(this.newLinkUrl)
    }, this.desktopFile, this.mobileFile ?? undefined).subscribe({
      next: banner => {
        this.banners.push(this.toEditor(banner));
        this.banners.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
        this.successMessage = 'Banner creado correctamente.';
        this.creating = false;
        this.resetCreateForm(form);
      },
      error: error => {
        this.errorMessage = this.errorText(error);
        this.creating = false;
      }
    });
  }

  saveMetadata(banner: BannerEditor): void {
    this.runBannerAction(banner, this.bannerService.updateMetadata(banner.id, {
      title: banner.draftTitle.trim(),
      altText: banner.draftAltText.trim(),
      linkUrl: this.cleanOptional(banner.draftLinkUrl)
    }), 'Cambios guardados.');
  }

  toggleActive(banner: BannerEditor): void {
    this.runBannerAction(
      banner,
      this.bannerService.setActive(banner.id, !banner.active),
      banner.active ? 'Banner desactivado.' : 'Banner activado.'
    );
  }

  replaceImage(banner: BannerEditor, target: 'desktop' | 'mobile', event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    const validation = this.validateImage(file);
    if (validation) {
      this.errorMessage = validation;
      input.value = '';
      return;
    }
    const request = target === 'desktop'
      ? this.bannerService.replaceDesktop(banner.id, file)
      : this.bannerService.replaceMobile(banner.id, file);
    this.runBannerAction(banner, request, `Imagen ${target} actualizada.`, () => input.value = '');
  }

  removeMobile(banner: BannerEditor): void {
    this.runBannerAction(banner, this.bannerService.removeMobile(banner.id), 'Imagen mobile eliminada.');
  }

  moveBanner(index: number, direction: -1 | 1): void {
    const target = index + direction;
    if (this.ordering || target < 0 || target >= this.banners.length) {
      return;
    }
    const previousOrder = [...this.banners];
    [this.banners[index], this.banners[target]] = [this.banners[target], this.banners[index]];
    this.ordering = true;
    this.clearMessages();
    this.bannerService.reorder(this.banners.map(banner => banner.id)).subscribe({
      next: banners => {
        this.banners = banners.map(banner => this.toEditor(banner));
        this.successMessage = 'Orden actualizado.';
        this.ordering = false;
      },
      error: error => {
        this.banners = previousOrder;
        this.errorMessage = this.errorText(error);
        this.ordering = false;
      }
    });
  }

  deleteBanner(banner: BannerEditor): void {
    if (!window.confirm(`¿Eliminar el banner "${banner.title}"?`)) {
      return;
    }
    banner.busy = true;
    this.clearMessages();
    this.bannerService.delete(banner.id).subscribe({
      next: () => {
        this.banners = this.banners.filter(item => item.id !== banner.id);
        this.successMessage = 'Banner eliminado.';
      },
      error: error => {
        banner.busy = false;
        this.errorMessage = this.errorText(error);
      }
    });
  }

  hasCustomMobile(banner: BannerEditor): boolean {
    return banner.mobileImageUrl !== banner.desktopImageUrl;
  }

  private runBannerAction(
    banner: BannerEditor,
    request: ReturnType<HomeBannerService['updateMetadata']>,
    success: string,
    finalize?: () => void
  ): void {
    banner.busy = true;
    this.clearMessages();
    request.subscribe({
      next: updated => {
        this.replaceBanner(updated);
        this.successMessage = success;
        finalize?.();
      },
      error: error => {
        banner.busy = false;
        this.errorMessage = this.errorText(error);
        finalize?.();
      }
    });
  }

  private replaceBanner(updated: AdminHomeBanner): void {
    const index = this.banners.findIndex(banner => banner.id === updated.id);
    if (index >= 0) {
      this.banners[index] = this.toEditor(updated);
    }
  }

  private toEditor(banner: AdminHomeBanner): BannerEditor {
    return {
      ...banner,
      draftTitle: banner.title,
      draftAltText: banner.altText,
      draftLinkUrl: banner.linkUrl ?? '',
      busy: false
    };
  }

  private validateImage(file: File): string | null {
    if (file.size > 5 * 1024 * 1024) {
      return 'La imagen no puede superar los 5 MB.';
    }
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    const hasValidExtension = /\.(jpe?g|png|webp)$/i.test(file.name);
    if (!allowedTypes.includes(file.type) || !hasValidExtension) {
      return 'Usá una imagen JPEG, PNG o WEBP válida.';
    }
    return null;
  }

  private cleanOptional(value: string): string | null {
    return value.trim() || null;
  }

  private resetCreateForm(form: NgForm): void {
    this.revokePreview(this.desktopPreview);
    this.revokePreview(this.mobilePreview);
    this.desktopFile = null;
    this.mobileFile = null;
    this.desktopPreview = '';
    this.mobilePreview = '';
    form.resetForm();
  }

  private revokePreview(url: string): void {
    if (url.startsWith('blob:')) {
      URL.revokeObjectURL(url);
    }
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  private errorText(error: unknown): string {
    if (error && typeof error === 'object' && 'error' in error) {
      const body = (error as { error?: unknown }).error;
      if (typeof body === 'string' && body.trim()) {
        return body;
      }
      if (body && typeof body === 'object' && 'message' in body) {
        const message = (body as { message?: unknown }).message;
        if (typeof message === 'string' && message.trim()) {
          return message;
        }
      }
    }
    return 'No se pudo completar la operación.';
  }
}
