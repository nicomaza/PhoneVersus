import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, concatMap, filter, finalize, from, map, switchMap, take, timer } from 'rxjs';
import {
  ProductContentDetail,
  ProductContentHighlight,
  ProductContentSaveRequest,
  ProductContentSpecSection
} from '../models/admin-config.models';
import { AdminConfigService } from '../services/admin-config.service';

@Component({
  selector: 'app-product-content-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './product-content-editor.component.html',
  styleUrl: './product-content-editor.component.css'
})
export class ProductContentEditorComponent implements OnInit, OnDestroy {
  content: ProductContentDetail | null = null;
  loading = false;
  saving = false;
  uploading = false;
  syncingFromPorte = false;
  porteSyncMessage = '';
  dragActive = false;
  error = '';
  message = '';

  private slug = '';
  private draftBaseline = '';
  private readonly subscriptions = new Subscription();

  constructor(private route: ActivatedRoute, private service: AdminConfigService) {}

  ngOnInit(): void {
    this.slug = this.route.snapshot.paramMap.get('slug') ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.subscriptions.add(this.service.getProductContent(this.slug)
      .pipe(finalize(() => this.loading = false))
      .subscribe({ next: content => this.applyContent(content), error: (error: Error) => this.error = error.message }));
  }

  addHighlight(): void {
    this.content?.highlights.push({ text: '' });
  }

  removeHighlight(index: number): void {
    this.content?.highlights.splice(index, 1);
  }

  moveHighlight(index: number, direction: number): void {
    if (!this.content) return;
    this.move(this.content.highlights, index, direction);
  }

  addSection(): void {
    this.content?.specSections.push({ title: '', specs: [] });
  }

  removeSection(index: number): void {
    this.content?.specSections.splice(index, 1);
  }

  moveSection(index: number, direction: number): void {
    if (!this.content) return;
    this.move(this.content.specSections, index, direction);
  }

  addSpec(section: ProductContentSpecSection): void {
    section.specs.push({ label: '', value: '' });
  }

  removeSpec(section: ProductContentSpecSection, index: number): void {
    section.specs.splice(index, 1);
  }

  moveSpec(section: ProductContentSpecSection, index: number, direction: number): void {
    this.move(section.specs, index, direction);
  }

  saveDraft(): void {
    if (!this.content || this.saving) return;
    this.runSave(this.service.saveProductContentDraft(this.slug, this.request()), 'Borrador guardado.');
  }

  publish(): void {
    if (!this.content || this.saving) return;
    this.saving = true;
    this.clearFeedback();
    this.subscriptions.add(this.service.saveProductContentDraft(this.slug, this.request()).pipe(
      switchMap(() => this.service.publishProductContent(this.slug)),
      finalize(() => this.saving = false)
    ).subscribe({
      next: content => { this.applyContent(content); this.message = 'Contenido publicado.'; },
      error: (error: Error) => this.error = error.message
    }));
  }

  unpublish(): void {
    if (!this.content || this.saving || this.content.status !== 'PUBLISHED') return;
    this.saving = true;
    this.clearFeedback();
    this.subscriptions.add(this.service.unpublishProductContent(this.slug)
      .pipe(finalize(() => this.saving = false))
      .subscribe({
        next: content => { this.applyContent(content); this.message = 'Contenido despublicado; el borrador se conserva.'; },
        error: (error: Error) => this.error = error.message
      }));
  }

  updateFromPorte(): void {
    if (!this.content || this.syncingFromPorte || this.saving) return;

    if (!this.hasUnsavedDraftChanges()) {
      this.startIndividualPorteSync();
      return;
    }

    if (window.confirm('Hay cambios sin guardar. ¿Querés guardarlos antes de actualizar desde Tienda Porte?')) {
      this.saving = true;
      this.clearFeedback();
      this.subscriptions.add(this.service.saveProductContentDraft(this.slug, this.request()).pipe(
        finalize(() => this.saving = false)
      ).subscribe({
        next: content => {
          this.applyContent(content);
          this.startIndividualPorteSync();
        },
        error: (error: Error) => this.error = error.message
      }));
      return;
    }

    if (window.confirm('¿Descartar los cambios sin guardar y recargar el contenido desde Tienda Porte?')) {
      this.startIndividualPorteSync();
    }
  }

  chooseFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.uploadFiles(Array.from(input.files ?? []));
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    this.uploadFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  saveImageAlt(imageId: number, altText: string): void {
    this.runImageChange(this.service.updateProductImageAlt(this.slug, imageId, altText), 'Texto ALT guardado.');
  }

  setPrimary(imageId: number): void {
    this.runImageChange(this.service.setPrimaryProductImage(this.slug, imageId), 'Imagen principal actualizada.');
  }

  deleteImage(imageId: number): void {
    if (!window.confirm('¿Eliminar esta imagen del borrador?')) return;
    this.runImageChange(this.service.deleteProductImage(this.slug, imageId), 'Imagen eliminada.');
  }

  moveImage(index: number, direction: number): void {
    if (!this.content) return;
    const next = index + direction;
    if (next < 0 || next >= this.content.images.length) return;
    this.move(this.content.images, index, direction);
    this.runImageChange(
      this.service.reorderProductImages(this.slug, this.content.images.map(image => image.id)),
      'Orden de imágenes actualizado.'
    );
  }

  statusLabel(): string {
    if (this.content?.status === 'PUBLISHED') {
      return this.content.hasUnpublishedChanges ? 'Publicado · cambios sin publicar' : 'Publicado';
    }
    return this.content?.status === 'DRAFT' ? 'Borrador' : 'Sin contenido';
  }

  private uploadFiles(files: File[]): void {
    const allowed = files.filter(file => ['image/jpeg', 'image/png', 'image/webp'].includes(file.type));
    if (allowed.length !== files.length) {
      this.error = 'Solo se permiten imágenes JPEG, PNG o WEBP.';
    }
    if (allowed.length === 0 || this.uploading) return;
    this.uploading = true;
    this.clearFeedback();
    this.subscriptions.add(from(allowed).pipe(
      concatMap(file => this.service.uploadProductImage(this.slug, file, '')),
      finalize(() => this.uploading = false)
    ).subscribe({
      next: content => this.applyContent(content),
      complete: () => this.message = 'Imágenes cargadas. Publicá para hacerlas visibles.',
      error: (error: Error) => this.error = error.message
    }));
  }

  private request(): ProductContentSaveRequest {
    const content = this.content!;
    return {
      description: content.description ?? '',
      sourceUrl: content.sourceUrl ?? '',
      sourceName: content.sourceName ?? '',
      highlights: content.highlights.map(item => ({ id: item.id, text: item.text })),
      specSections: content.specSections.map(section => ({
        id: section.id,
        title: section.title,
        specs: section.specs.map(spec => ({ id: spec.id, label: spec.label, value: spec.value }))
      }))
    };
  }

  private runSave(request: ReturnType<AdminConfigService['saveProductContentDraft']>, message: string): void {
    this.saving = true;
    this.clearFeedback();
    this.subscriptions.add(request.pipe(finalize(() => this.saving = false)).subscribe({
      next: content => { this.applyContent(content); this.message = message; },
      error: (error: Error) => this.error = error.message
    }));
  }

  private runImageChange(request: ReturnType<AdminConfigService['deleteProductImage']>, message: string): void {
    this.clearFeedback();
    this.subscriptions.add(request.subscribe({
      next: content => { this.applyContent(content); this.message = message; },
      error: (error: Error) => this.error = error.message
    }));
  }

  private move<T>(items: T[], index: number, direction: number): void {
    const next = index + direction;
    if (next < 0 || next >= items.length) return;
    [items[index], items[next]] = [items[next], items[index]];
  }

  private clearFeedback(): void {
    this.error = '';
    this.message = '';
    this.porteSyncMessage = '';
  }

  private startIndividualPorteSync(): void {
    this.syncingFromPorte = true;
    this.clearFeedback();
    this.porteSyncMessage = 'Obteniendo características desde Porte...';
    this.subscriptions.add(this.service.syncProductContentFromPorte(this.slug).subscribe({
      next: response => {
        if (!response.started) {
          this.syncingFromPorte = false;
          this.porteSyncMessage = '';
          this.message = response.message;
          return;
        }

        this.subscriptions.add(timer(500, 1000).pipe(
          switchMap(() => this.service.getPorteProductContentSyncStatus()),
          filter(status => !status.running),
          take(1),
          switchMap(status => this.service.getProductContent(this.slug).pipe(
            map(content => ({ status, content }))
          )),
          finalize(() => {
            this.syncingFromPorte = false;
            this.porteSyncMessage = '';
          })
        ).subscribe({
          next: ({ status, content }) => {
            this.applyContent(content);
            if (status.errors > 0) {
              this.error = status.lastError?.trim() || 'No se pudo actualizar el contenido desde Porte.';
            } else if (status.withoutSheet > 0) {
              this.message = 'No hay una ficha de características disponible en Porte para este equipo.';
            } else {
              this.message = 'Contenido actualizado desde Tienda Porte. Revisá el borrador antes de publicar.';
            }
          },
          error: (error: Error) => this.error = error.message
        }));
      },
      error: (error: Error) => {
        this.syncingFromPorte = false;
        this.porteSyncMessage = '';
        this.error = error.message;
      }
    }));
  }

  private hasUnsavedDraftChanges(): boolean {
    return !!this.content && JSON.stringify(this.request()) !== this.draftBaseline;
  }

  private applyContent(content: ProductContentDetail): void {
    this.content = content;
    this.draftBaseline = JSON.stringify(this.request());
  }
}
