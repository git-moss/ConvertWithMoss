set SOURCE_PATH=C:\Privat\Programming\SampleConverter\SampleConverter

docker run --rm -v "%SOURCE_PATH%:/data" pandoc/latex --standalone --toc -V geometry:margin=2.5cm --number-sections -o ConvertWithMoss-Manual.pdf README.md
