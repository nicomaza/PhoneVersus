import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { Phone } from '../models/phone';
import { CommonModule } from '@angular/common';
import { PhonesService } from '../services/phones.service';
import { IonicModule } from '@ionic/angular';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, Observable, of, switchMap } from 'rxjs';
import { searchedPhone } from '../models/searchedPhone';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-comparatives',
  standalone: true,
  imports: [CommonModule, IonicModule, FormsModule, RouterLink],
  templateUrl: './comparatives.component.html',
  styleUrl: './comparatives.component.css'
})
export class ComparativesComponent implements OnInit {
  @Input('id') idphone!: string;

  phoneleft: Phone | undefined = undefined;

  phoneright: Phone | undefined = undefined;;
  IDleft: number = 0;
  IDright: number = 0;

  searchText: string = '';
  searchTextRight: string = '';

  rightclass = false;
  leftclass = false;


  phones$: Observable<searchedPhone[]> = of([]);
  isLoading: boolean = false;
  isLoadingRight: boolean = false;

  constructor(private phoneservice: PhonesService, private cdr: ChangeDetectorRef) { }

  ngOnInit(): void {
    
   this.setPhoneLeft(parseInt(this.idphone))
   if (this.idphone) {
    this.setPhoneLeft(parseInt(this.idphone));
  }
  }

  deletePhoneLeft(){
    this.phoneleft = undefined;
    this.cdr.detectChanges(); // Detect changes
  }
  deletePhoneRight(){
    this.phoneright = undefined;
    this.cdr.detectChanges(); // Detect changes
  }


  setPhoneLeft(id: number) {
    this.phoneservice.getPhoneById(id).subscribe(
      (data: Phone) => {
        this.phones$ = of([]);
        this.phoneleft = data;
      },
      (error) => {
        console.log(error)
      }
    )
  }
 

  setPhoneRight(id: number) {
    this.phoneservice.getPhoneById(id).subscribe(
      (data: Phone) => {
        this.phones$ = of([]);
        this.phoneright = data;

      },
      (error) => {
        console.log(error)
      }
    )
  }

  isBothLoaded(){

   
    if(this.phoneleft == undefined){
        this.leftclass = true
        
     }else if(this.phoneright == undefined){
        this.rightclass = true
             }
  }

  isPhoneLeftLoaded(): boolean {
    return !this.phoneleft;
  }

  isPhoneRightLoaded(): boolean {

    
    return !this.phoneright;
  }


  getRouterLink(): string[] | null {
    if (this.phoneleft?.idPhone && this.phoneright?.idPhone) {
      // Verificar si el ID fue pasado por @Input y ajustar la ruta
      return this.idphone ? ['../../versus', this.phoneleft.idPhone.toString(), this.phoneright.idPhone.toString()]
                          : ['../versus', this.phoneleft.idPhone.toString(), this.phoneright.idPhone.toString()];
    }
    return null;
  }
  

  onSearch(value: string): void {
    if (value.trim() === '') {
      // Si el valor de búsqueda está vacío, no realizar la búsqueda y establecer phones$ a un Observable vacío
      this.phones$ = of([]);
      return;
    }
    this.isLoadingRight = false;
    this.isLoading = true;
    this.phones$ = of(value).pipe(
      debounceTime(300), // Espera 300ms después de que el usuario deja de escribir
      distinctUntilChanged(), // Evita búsquedas repetidas para el mismo valor
      switchMap((searchText: string) =>
        this.phoneservice.phoneSearcher(searchText).pipe(
          map((phones: searchedPhone[]) => phones.map(phone => ({
            ...phone,
            // Formatear los datos según sea necesario
            brand: phone.brand.toUpperCase(), // Ejemplo: poner en mayúsculas
            // Ejemplo: poner en minúsculas

          }))),
          catchError(() => of([])), // Maneja errores devolviendo un array vacío
          finalize(() => {
            this.isLoadingRight = false;
            this.cdr.detectChanges(); // Marcar cambios después de actualizar isLoading
          })
        )
      )
    );

    this.phones$.subscribe((data) => console.log(data))
  }



  onSearchRight(value: string): void {
    if (value.trim() === '') {
      // Si el valor de búsqueda está vacío, no realizar la búsqueda y establecer phones$ a un Observable vacío
      this.phones$ = of([]);
      return;
    }
    this.isLoadingRight = true;
    this.isLoading = false;
    this.phones$ = of(value).pipe(
      debounceTime(300), // Espera 300ms después de que el usuario deja de escribir
      distinctUntilChanged(), // Evita búsquedas repetidas para el mismo valor
      switchMap((searchText: string) =>
        this.phoneservice.phoneSearcher(searchText).pipe(
          map((phones: searchedPhone[]) => phones.map(phone => ({
            ...phone,
            // Formatear los datos según sea necesario
            brand: phone.brand.toUpperCase(), // Ejemplo: poner en mayúsculas
            // Ejemplo: poner en minúsculas

          }))),
          catchError(() => of([])), // Maneja errores devolviendo un array vacío
          finalize(() => {
            this.isLoading = false;
            this.cdr.detectChanges(); // Marcar cambios después de actualizar isLoading
          })
        )
      )
    );

    this.phones$.subscribe((data) => console.log(data))
  }

}

/**
                        <div class="d-none d-sm-block ">
                            <img [src]="phoneleft.images[0]" class="" alt="{{ phoneleft.model }}"
                                style="width: 200; height: 400;" />
                        </div> */
/**
 * 
 */

/**
 *  <p><small class="" style="color: rgb(165, 165, 165);">Primer equipo a comparar
                                seleccionado</small></p>
 * 
                                        <p><small class="" style="color: rgb(165, 165, 165);">Segundo equipo a comparar
                                seleccionado</small></p>
 * 
 */