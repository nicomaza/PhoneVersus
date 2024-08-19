import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Phone } from '../models/phone';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { PhonesService } from '../services/phones.service';
import { Observable, of } from 'rxjs';

@Component({
  selector: 'app-phone-item',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './phone-item.component.html',
  styleUrl: './phone-item.component.css'
})
export class PhoneItemComponent implements OnInit, OnChanges {
  @Input() phone!: Phone | undefined;
  ruta: string = '';
  phoneidstring: string = "";

  phone2!: Phone;
  phone$: Observable<Phone> = of();

  constructor(private phoneService: PhonesService) { }

  ngOnInit(): void {
    if (this.phone?.idPhone !== undefined) {
  
      this.phone$ = this.phoneService.getPhoneById(this.phone?.idPhone);
      this.phoneService.getPhoneById(this.phone?.idPhone).subscribe(
        (data: Phone) => {
          this.phone = data;
  
          // Si existe el array de imágenes y tiene más de un elemento
          if (this.phone.images && this.phone.images.length > 0) {
            // Crear un nuevo array sin el último elemento
            const updatedImages = this.phone.images.slice(0, -1);
            this.phone.images = updatedImages; // Asignamos el nuevo array
          }
  
          console.log('Phone with updated images:', this.phone);
        },
        (error) => {
          console.error('Error fetching phone', error);
        }
      );
    }
  }
  

  ngOnChanges(changes: SimpleChanges): void {

  }





}
