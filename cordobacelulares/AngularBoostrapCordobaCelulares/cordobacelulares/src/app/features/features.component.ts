import { Component, Input, OnInit } from '@angular/core';
import { Phone } from '../models/phone';
import { routes } from '../app.routes';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HomeComponent } from '../home/home.component';
import { PhonesService } from '../services/phones.service';
import { Observable, of } from 'rxjs';

@Component({
  selector: 'app-features',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule, HomeComponent],
  templateUrl: './features.component.html',
  styleUrl: './features.component.css'
})
export class FeaturesComponent implements OnInit {
  @Input('id') idphone!: string;
  phone!: Phone;
  phone$: Observable<Phone> = of(); ; 

  constructor(private phoneService: PhonesService) { }

  ngOnInit(): void {
    const numericId = parseInt(this.idphone, 10);
    this.phone$ = this.phoneService.getPhoneById(numericId);
    this.phoneService.getPhoneById(numericId).subscribe(
      (data: Phone) => {
        this.phone = data;
        console.log(this.phone)
      },
      (error) => {
        console.error('Error fetching phones', error);
      }
    )
    
  }


/**  message: string = `Hola!! Quiero informacion de este modelo: ${this.phone.model}`
  sendWhatsAppMessage() {
    const phoneNumber = '5493517576319'; // Reemplaza con el número de teléfono
    const message = `Hola, necesito informacion del siguiente modelo: ${this.phone.brand + " " + this.phone.model}`; // Reemplaza con tu mensaje
    const url = `https://api.whatsapp.com/send?phone=${phoneNumber}&text=${encodeURIComponent(message)}`;
    window.open(url, '_blank');
  } */
}


/**{
    id: 1,
  
    brand: "Samsung",
    model: "Galaxy S21",
    version: "5G",
  images: ["https://d28hi93gr697ol.cloudfront.net/9ef84dda-32dd-4016-7da3-1c0a824fffb4/img/Producto/15518619-bfc8-377d-6a3c-d3c821884b8a/Diseno-sin-titulo-64-65be63e2f14e1.png","https://assets.swappie.com/cdn-cgi/image/width=600,height=600,fit=contain,format=auto/swappie-iphone-14-pro-max-gold.png?v=15e7e016"],
    mainCamera: ["108 MP (f/1.75, PDAF, OIS, wide)", "12 MP (f/2.2, ultrawide)", "10 MP (f/2.4, telephoto)"],
    secondaryCamera: "40 MP (f/2.2, wide)",
    screen: ["6.2 inches, 1080 x 2400 pixels, Dynamic AMOLED 2X"],
    processor: "Exynos 2100",
    gpu: "Mali-G78 MP14",
    memory: ["8 GB RAM", "128 GB internal storage"],
    expansion: "No",
    os: "Android 11, upgradable to Android 12",
    battery: ["4000 mAh", "Fast charging 25W"],
    connectivity: ["5G", "Wi-Fi 6", "Bluetooth 5.2", "NFC"],
    dimensions: "151.7 x 71.2 x 7.9 mm",
    security: ["Fingerprint (under display, ultrasonic)", "Face recognition"],
    colors: ["Phantom Gray", "Phantom White", "Phantom Violet", "Phantom Pink"],
    boxContents: ["Phone", "USB-C cable", "Ejection pin", "Quick Start Guide"]
  } */