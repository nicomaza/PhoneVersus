import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { boxcontent } from '../models/boxcontent';

@Injectable({
  providedIn: 'root'
})
export class BoxcontentService {

  constructor(private http:HttpClient) {   }



      private apiUrl = '/api/boxcontent';

     getAllBoxContent(): Observable<boxcontent[]> {
        return this.http.get<boxcontent[]>(this.apiUrl);
      }

}
