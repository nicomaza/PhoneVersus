package celulares.cordobacelulares.dtos.enums;

public enum Colors {
    Ice_Blue,
    Pearl_Green,
    Onyx_Black,

    Forest_Green,

    Graphite_Gray,

    Midnight_Blue;

    @Override
    public String toString() {
        // Convertir el nombre en el formato deseado
        return name().replace('_', ' ');
    }
}
