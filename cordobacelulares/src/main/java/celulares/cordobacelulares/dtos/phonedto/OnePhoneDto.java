package celulares.cordobacelulares.dtos.phonedto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OnePhoneDto {
    private Long idPhone;

    private List<String> images;

    private List<String> mainCamera;

    private String secondaryCamera;

    private List<String> screen;

    private String processor;
    private String gpu;

    private List<String> memory;

    private String expansion;
    private String os;

    private List<String> battery;

    private List<String> connectivity;

    private String dimensions;

    private List<String> security;

    private List<String> colors;

    private List<String> boxContents;

    private String model;

    private String brand;
    private String videoYoutube;
    private String red;
}
