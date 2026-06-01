namespace DesklyPC;

public sealed record DisplayInfo(
    string id,              // stabilné ID
    string name,            // "Internal display" / "Dell U2720Q"
    DisplayType type,
    bool supportsBrightness
);
