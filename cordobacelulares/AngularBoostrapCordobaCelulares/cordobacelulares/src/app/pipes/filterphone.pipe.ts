import { Pipe, PipeTransform } from '@angular/core';
import { Phone } from '../models/phone';

@Pipe({
  name: 'filterphone',
  standalone: true
})
export class FilterphonePipe implements PipeTransform {

  transform(phones: Phone[], search: string, page: number): Phone[] {
    if (!phones || !search) {
      return phones;
    }
    // Convertir búsqueda en minúsculas y dividir en palabras
    const searchTerms = search.toLowerCase().trim().split(/\s+/);

    return phones
      .filter(phone =>
        searchTerms.every(term =>
          phone.brand.toLowerCase().includes(term) ||
          phone.model.toLowerCase().includes(term)
        )
      )
      .slice(page, page + 6);
  }

}



