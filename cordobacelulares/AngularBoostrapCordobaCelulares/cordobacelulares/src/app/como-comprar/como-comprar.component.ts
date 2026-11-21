import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogWhatsappService } from '../services/catalog-whatsapp.service';

@Component({
  selector: 'app-como-comprar',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './como-comprar.component.html',
  styleUrl: './como-comprar.component.css'
})
export class ComoComprarComponent {
  constructor(private whatsappService: CatalogWhatsappService) { }

  consultar(): void {
    this.whatsappService.openGeneralConsultation();
  }
}
