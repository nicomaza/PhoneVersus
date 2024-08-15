import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Phone } from '../models/phone';
import { searchedPhone } from '../models/searchedPhone';

@Injectable({
  providedIn: 'root'
})

@Injectable({ providedIn: 'root' })
export class PhonesService {

  private apiUrl = 'https://www.cordobacelulares.com/api/phones';

  productos: Phone[] = [];


  constructor(private http: HttpClient) { }


  getAllPhones(): Observable<Phone[]> {
    return this.http.get<Phone[]>(this.apiUrl);
  }
  getPhoneById(id: number): Observable<Phone> {
    return this.http.get<Phone>(`${this.apiUrl}/${id}`);

  }
  getPhonesByBrand(brand:string): Observable<Phone[]> {
    return this.http.get<Phone[]>(`${this.apiUrl}/brand/${brand}`);
  }
  phoneSearcher(textSearch:string):Observable<searchedPhone[]>{
    return this.http.get<searchedPhone[]>(`${this.apiUrl}/search?textsearch=${textSearch}`);
  }


}


/**
 *   page: number = 0;
 *   updatePage(pagenew: number) {
    this.page = pagenew;
  }
  getPage() {
    return this.page;
  }

  listProducts(): Phone[] {
    return this.productos;
  }

 *
 *
 *
 * [


    {
      idPhone: 1,

      brand: "Samsung",
      model: "Galaxy S21",
      version: "5G",
    images: ["https://d28hi93gr697ol.cloudfront.net/9ef84dda-32dd-4016-7da3-1c0a824fffb4/img/Producto/15518619-bfc8-377d-6a3c-d3c821884b8a/Diseno-sin-titulo-64-65be63e2f14e1.png"],
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
    },
    {
      idPhone: 2,

      brand: "Samsung",
      model: "Galaxy S21",
      version: "5G",
    images: ["https://d28hi93gr697ol.cloudfront.net/9ef84dda-32dd-4016-7da3-1c0a824fffb4/img/Producto/15518619-bfc8-377d-6a3c-d3c821884b8a/Diseno-sin-titulo-64-65be63e2f14e1.png"],
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
    },
   {
    idPhone: 3,

      brand: "Apple",
      model: "iPhone 13 Pro",
      version: "A2639",
      images: ["https://images.fravega.com/f300/31748935fdb542bb843f3803c7972829.jpg.webp"],
      mainCamera: ["12 MP (f/1.5, wide)", "12 MP (f/1.8, ultrawide)", "12 MP (f/2.8, telephoto)"],
      secondaryCamera: "12 MP (f/2.2, wide)",
      screen: ["6.1 inches, 1170 x 2532 pixels, Super Retina XDR OLED"],
      processor: "Apple A15 Bionic",
      gpu: "Apple GPU (5-core graphics)",
      memory: ["6 GB RAM", "256 GB internal storage"],
      expansion: "No",
      os: "iOS 15",
      battery: ["3095 mAh", "Fast charging 20W"],
      connectivity: ["5G", "Wi-Fi 6", "Bluetooth 5.0", "NFC"],
      dimensions: "146.7 x 71.5 x 7.7 mm",
      security: ["Face ID"],
      colors: ["Graphite", "Gold", "Silver", "Sierra Blue"],
      boxContents: ["Phone", "USB-C to Lightning cable", "Documentation"]
    },
   {
    idPhone: 4,
      brand: "Google",
      model: "Pixel 6 Pro",
      version: "2021",
      images: ["https://images.fravega.com/f300/31748935fdb542bb843f3803c7972829.jpg.webp"],

      mainCamera: [
        "50 MP (f/1.9, PDAF, Laser AF, OIS, wide)",
        "12 MP (f/2.2, ultrawide)",
        "48 MP (f/3.5, PDAF, OIS, telephoto)"
      ],
      secondaryCamera: "11.1 MP (f/2.2, ultrawide)",
      screen: ["6.71 inches, 1440 x 3120 pixels, LTPO AMOLED, 120Hz"],
      processor: "Google Tensor",
      gpu: "Mali-G78 MP20",
      memory: ["12 GB RAM"],
      expansion: "No",
      os: "Android 12",
      battery: ["5003 mAh", "30W fast charging", "23W wireless charging"],
      connectivity: ["5G", "Wi-Fi 6E", "Bluetooth 5.2", "NFC", "USB Type-C 3.1"],
      dimensions: "163.9 x 75.9 x 8.9 mm",
      security: ["In-display fingerprint sensor", "Face unlock"],
      colors: ["Cloudy White", "Sorta Sunny", "Stormy Black"],
      boxContents: ["Phone", "Charger", "USB Cable", "Quick Switch Adapter", "SIM Eject Tool", "Quick Start Guide"]
    }

  ]; */
