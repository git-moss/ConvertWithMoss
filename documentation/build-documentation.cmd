set SOURCE_PATH=C:\Privat\Programming\ConvertWithMoss\ConvertWithMoss\trunk

docker run --rm -v "%SOURCE_PATH%:/data" pandoc/latex --standalone --toc -V geometry:margin=2.5cm --number-sections --output=ConvertWithMoss-Manual.pdf documentation/README.md documentation/README-FORMATS.md documentation/CHANGELOG.md