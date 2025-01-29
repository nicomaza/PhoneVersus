package celulares.cordobacelulares.dtos.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ModelNewDto {

    String model;
    Long idModel;
    String brand;
    Long idBrand;
}
