import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Phone } from '../models/phone';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

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

  ngOnInit(): void {

    if (this.phone?.images[0] == undefined) {
      this.ruta = 'error'
    } else {

      this.ruta = this.phone?.images[0];
    }

  }

  ngOnChanges(changes: SimpleChanges): void {

  }





}
