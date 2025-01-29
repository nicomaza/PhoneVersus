import { Injectable } from '@angular/core';
import {  ModelNewDto } from '../models/ModelNewDto';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class ModelService {

  constructor(private http: HttpClient) { }
    private apiUrl = 'https://www.cordobacelulares.com/api/models/dtos';
 //private apiUrl = 'http://localhost:8080/api/brand';
  getAllBrands(): Observable<ModelNewDto[]> {
    return this.http.get<ModelNewDto[]>(this.apiUrl);
  }

}
