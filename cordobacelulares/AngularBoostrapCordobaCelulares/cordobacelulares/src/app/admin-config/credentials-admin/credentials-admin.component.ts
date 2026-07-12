import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminConfigService } from '../services/admin-config.service';
import { CredentialPatchRequest, CredentialResponse } from '../models/admin-config.models';

@Component({
  selector: 'app-credentials-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './credentials-admin.component.html',
  styleUrl: './credentials-admin.component.css'
})
export class CredentialsAdminComponent implements OnInit {
  credentials: CredentialResponse[] = [];
  loading = false;
  saving = false;
  error = '';
  success = '';
  showForm = false;
  showPassword = false;
  editingCredential: CredentialResponse | null = null;
  credentialToDelete: CredentialResponse | null = null;

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.maxLength(300)]],
    activa: [true]
  });

  constructor(
    private formBuilder: FormBuilder,
    private adminConfigService: AdminConfigService
  ) { }

  ngOnInit(): void {
    this.loadCredentials();
  }

  loadCredentials(): void {
    this.loading = true;
    this.error = '';
    this.adminConfigService.getCredentials().subscribe({
      next: credentials => {
        this.credentials = credentials;
      },
      error: (error: Error) => {
        this.error = error.message;
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  openCreate(): void {
    this.editingCredential = null;
    this.showPassword = false;
    this.showForm = true;
    this.success = '';
    this.error = '';
    this.form.reset({ username: '', password: '', activa: true });
    this.form.controls.password.setValidators([Validators.required, Validators.maxLength(300)]);
    this.form.controls.password.updateValueAndValidity();
  }

  openEdit(credential: CredentialResponse): void {
    this.editingCredential = credential;
    this.showPassword = false;
    this.showForm = true;
    this.success = '';
    this.error = '';
    this.form.reset({
      username: credential.username,
      password: '',
      activa: credential.activa
    });
    this.form.controls.password.setValidators([Validators.maxLength(300)]);
    this.form.controls.password.updateValueAndValidity();
  }

  closeForm(): void {
    this.showForm = false;
    this.editingCredential = null;
    this.showPassword = false;
    this.form.reset({ username: '', password: '', activa: true });
  }

  save(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const username = raw.username.trim();
    const password = raw.password.trim();

    if (!username) {
      this.form.controls.username.setErrors({ required: true });
      return;
    }

    this.saving = true;
    this.error = '';
    this.success = '';

    if (this.editingCredential) {
      const request: CredentialPatchRequest = {
        username,
        activa: raw.activa
      };
      if (password) {
        request.password = password;
      }
      this.adminConfigService.patchCredential(this.editingCredential.id, request).subscribe({
        next: () => {
          this.success = 'Credencial actualizada.';
          this.closeForm();
          this.loadCredentials();
        },
        error: (error: Error) => {
          this.error = error.message;
          this.saving = false;
        },
        complete: () => {
          this.saving = false;
        }
      });
      return;
    }

    if (!password) {
      this.form.controls.password.setErrors({ required: true });
      this.saving = false;
      return;
    }

    this.adminConfigService.createCredential({ username, password, activa: raw.activa }).subscribe({
      next: () => {
        this.success = 'Credencial creada.';
        this.closeForm();
        this.loadCredentials();
      },
      error: (error: Error) => {
        this.error = error.message;
        this.saving = false;
      },
      complete: () => {
        this.saving = false;
      }
    });
  }

  toggleActive(credential: CredentialResponse): void {
    if (this.saving) {
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';
    this.adminConfigService.patchCredential(credential.id, { activa: !credential.activa }).subscribe({
      next: () => {
        this.success = credential.activa ? 'Credencial desactivada.' : 'Credencial activada.';
        this.loadCredentials();
      },
      error: (error: Error) => {
        this.error = error.message;
        this.saving = false;
      },
      complete: () => {
        this.saving = false;
      }
    });
  }

  requestDelete(credential: CredentialResponse): void {
    this.credentialToDelete = credential;
  }

  cancelDelete(): void {
    this.credentialToDelete = null;
  }

  deleteCredential(): void {
    if (!this.credentialToDelete || this.saving) {
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';
    const id = this.credentialToDelete.id;
    this.adminConfigService.deleteCredential(id).subscribe({
      next: () => {
        this.success = 'Credencial eliminada.';
        this.credentialToDelete = null;
        this.loadCredentials();
      },
      error: (error: Error) => {
        this.error = error.message;
        this.saving = false;
      },
      complete: () => {
        this.saving = false;
      }
    });
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '-';
    }
    return new Date(value).toLocaleString('es-AR');
  }

  trackByCredential(_: number, credential: CredentialResponse): number {
    return credential.id;
  }
}
