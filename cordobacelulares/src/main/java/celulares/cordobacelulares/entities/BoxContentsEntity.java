package celulares.cordobacelulares.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "box_content")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoxContentsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_content")
    private Long idContent;

    @Column(name = "name_content", nullable = false, unique = true)
    private String nameContent;
    @Override
    public String toString() {
        return this.nameContent;
    }
}
