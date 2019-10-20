# rsf-cmdline
Fork of the Java implementation of generalized Random Shapelet Forests (gRSF, from https://people.dsv.su.se/~isak-kar/grsf/ and https://github.com/isakkarlsson/rsf-cmdline) extended with Symbolic Aggregate approXimation (SAX) representation.

The code in this repository was used to generate results for my Master's thesis *"Time Series Classification: Evaluating Random Shapelet Forests with SAX representation"* in Information Systems Management. The full text of the thesis is available at https://www.researchgate.net/publication/336681088_Time_Series_Classification_Evaluating_Random_Shapelet_Forests_with_SAX_representation.

## Usage
Usage: java -jar rsf-cmdline.jar &lt;training datafile&gt; &lt;test datafile&gt; [-d] [-n] [-r] [-a] [-t] [-l] [-u] 

## Flags
* -d / --csv-delim: Present the results as a comma separated list
* -n / --no-trees: Number of trees, default: 100
* -r / --sample: Number of shapelets evaluated per node, default: 10
* -a / --alphabet-size: Alphabet size for the SAX conversion, default: 16
* -t / --ts-wordlength: Word length for the time series SAX convsersion, default: 15
* -l / --lower: Lower limit of word length for the shapelet SAX convsersion, specified as a proportion to the word length, default: 0.0
* -u / --upper: Upper limit of word length for the shapelet SAX convsersion, specified as a proportion to the word length, default: 1.0

## Datasets
The code was tested and used with datasets selected from the UCR Time Series Archive (available at https://www.cs.ucr.edu/~eamonn/time_series_data/ at the time, it has since moved to https://www.cs.ucr.edu/~eamonn/time_series_data_2018/).
