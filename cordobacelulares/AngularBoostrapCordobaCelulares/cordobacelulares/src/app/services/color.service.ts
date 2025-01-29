import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { color } from '../models/color';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ColorService {


  constructor(private http: HttpClient) { }
  private apiUrl = 'http://vps-4306850-x.dattaweb.com:8080/api/colors';
  getAllColors(): Observable<color[]> {
    return this.http.get<color[]>(this.apiUrl);
  }

}
