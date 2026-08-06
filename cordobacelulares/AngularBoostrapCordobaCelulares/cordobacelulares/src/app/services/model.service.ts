import { Injectable } from '@angular/core';
import {  ModelNewDto } from '../models/ModelNewDto';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class ModelService {

  constructor(private http: HttpClient) { }
    private apiUrl = '/api/models/dtos';
  getAllBrands(): Observable<ModelNewDto[]> {
    return this.http.get<ModelNewDto[]>(this.apiUrl);
  }

}
