import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormArray, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-newphone',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, FormsModule],
  templateUrl: './newphone.component.html',
  styleUrl: './newphone.component.css'
})
export class NewphoneComponent implements OnInit {

  phoneForm: FormGroup;

  constructor() {
    this.phoneForm = new FormGroup({
      idPhone: new FormControl(0, [Validators.required]),
      images: new FormArray([], [Validators.required]),
      mainCamera: new FormArray([], [Validators.required]),
      secondaryCamera: new FormArray([], [Validators.required]),
      red: new FormControl('', [Validators.required]),
      oficialWeb: new FormControl('', [Validators.required, Validators.pattern(/^https?:\/\/.+$/)]),
      screen: new FormArray([], [Validators.required]),
      processor: new FormControl('', [Validators.required, Validators.maxLength(50)]),
      gpu: new FormControl('', [Validators.required, Validators.maxLength(50)]),
      memory: new FormArray([], [Validators.required, Validators.maxLength(50)]),
      expansion: new FormControl('', [Validators.required]),
      os: new FormControl('', [Validators.required]),
      battery: new FormArray([], Validators.required),
      connectivity: new FormArray([], Validators.required),
      dimensions: new FormControl('', [Validators.required, Validators.maxLength(50)]), 
      security: new FormArray([], Validators.required),
      colors: new FormControl([], [Validators.required]),
      boxContents: new FormControl([], [Validators.required]),
      videoYoutube: new FormControl('', [Validators.required, Validators.pattern(/^https?:\/\/.+$/)]),
      idModel: new FormControl(0, [Validators.required]),
    });
  }

  ngOnInit(): void { }
  //FORM.ARRAY IMAGENES 
  get images(): FormArray {
    return this.phoneForm.get('images') as FormArray;
  }
  addImage(): void {
    this.images.push(new FormControl('', Validators.required));
  }

  removeImage(index: number): void {
    this.images.removeAt(index);
  }
  // FIN FORM.ARRAY IMAGENES



  // FORM.ARRAY CAMERA
  get mainCamera(): FormArray {
    return this.phoneForm.get('mainCamera') as FormArray;
  }
  addMainCamera(): void {
    this.mainCamera.push(new FormControl('', [Validators.required, Validators.maxLength(50)]));
  }
  removeMainCamera(index: number): void {
    this.mainCamera.removeAt(index);
  }

  // FIN FORM.ARRAY CAMERA


  // FORM.ARRAY SECONDARY CAMERA
  get secondaryCamera(): FormArray {
    return this.phoneForm.get('secondaryCamera') as FormArray;
  }
  addSecondaryCamera(): void {
    this.secondaryCamera.push(new FormControl('', [Validators.required, Validators.maxLength(50)]));
  }
  removeSecondaryCamera(index: number): void {
    this.secondaryCamera.removeAt(index);
  }

  // FIN FORM.ARRAY SECONDARY CAMERA


  // INICIO FORM.ARRAY  SCREEN
  get screen() {
    return this.phoneForm.controls['screen'] as FormArray;
  }
  addScreen(): void {
    this.screen.push(new FormControl('', [Validators.required, Validators.maxLength(50)]));
  }
  removeScreen(index: number) {
    this.screen.removeAt(index);
  }

  // FIN FORM.ARRAY SCREEN



  // INICIO FORM.ARRAY MEMORY

  get memory(): FormArray {
    return this.phoneForm.get('memory') as FormArray;
  }

  addMemory(): void {
    this.memory.push(new FormControl('', [Validators.required, Validators.maxLength(20)]));
  }

  removeMemory(index: number): void {
    this.memory.removeAt(index);
  }
  // FIN FORM.ARRAY MEMORY


  // INICIO FORM.ARRAY BATERY

  get battery(): FormArray {
    return this.phoneForm.get('battery') as FormArray;
  }

  addBattery(): void {
    this.battery.push(new FormControl('', [Validators.required, Validators.maxLength(100)]));
  }

  removeBattery(index: number): void {
    this.battery.removeAt(index);
  }
  // FIN FORM.ARRAY BATERY

  
  // INICIO FORM.ARRAY CONECTIVITY

  get connectivity(): FormArray {
    return this.phoneForm.get('connectivity') as FormArray;
  }


  addConnectivity(): void {
    this.connectivity.push(new FormControl('', [Validators.required, Validators.maxLength(100)]));
  }

  // Método para eliminar un control del FormArray
  removeConnectivity(index: number): void {
    this.connectivity.removeAt(index);
  }

  // FIN FORM.ARRAY conectivity


  
 // FIN FORM.ARRAY security

  get security(): FormArray {
    return this.phoneForm.get('security') as FormArray;
  }

  addSecurity(): void {
    this.security.push(new FormControl('', [Validators.required, Validators.maxLength(100)]));
  }

  // Método para eliminar un control del FormArray
  removeSecurity(index: number): void {
    this.security.removeAt(index);
  }

 // FIN FORM.ARRAY security








  onSubmit() {
    if (this.phoneForm.valid) {
      console.log('Formulario enviado:', this.phoneForm.value);
    } else {
      console.log('Formulario inválido');
    }
  }
}