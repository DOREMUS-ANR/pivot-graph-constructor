# pivot-graph-constructor
Algorithm for creating the pivot graph of works out of the interlinking process of DOREMUS datasets

The algorithm creates a graph that contains the union of all works (entities from the F22 class) contained in the DOREMUS bases, identified by nouvel URIs and linked via owl:sameAs statements to their correpsonding works in each base. When a link between two works across two bases has been established in the data linking process (see https://github.com/DOREMUS-ANR/knowledge-base/tree/master/linked-data), this link is also added to the pivot graph. 


This program uses four files, that need to be present in the store folder before execution :

	- finalResults.rdf, the concatenation of the three alignment files produced by Legato. These alignments need to be in good order (from base a to b, b to c, and finally c to a) for PGC to work properly.
	- bnf.ttl or bnf_f22only.ttl, the ttl file describing the bnf database (bnf_f22only is a trimmed down version of bnf, keeping only f22 expressions).
	- pp.ttl, same as bnf 
	- rf.ttl, same as bnf and pp

The three ttl files are only used to get all empty resources (f22 expressions only) to write them on the Pivot Graph. It is recommended to trim them down before, as loading large models in Jena is especially time and ram consuming.

Disclaimer : since this algorithm is designed to be used with bases containing only unique resource, without any duplicates, any multiple links from one resource to others are considered erroneous.

----------------
Arguments : 
----------------

As explained above, loading large models with Jena is very expensive in ram, the maximum memory have to be increased in order to run the program.
JVM Arguments : 

	- -d64       : to force the JVM to use 64 bits, i.e. more than 1Gb of ram)
	- -Xmx2048m  : sets the maximum memory allowed to the JVM. These values are an example, depending on the size of your models, you might need more or less ram. Specify the amount, and the unit used (m, g).

Optional argument : 
	You can specify a manual threshold for the sorting phase. It will be converted to a float. If no threshold value is entered, or if the value is not readable, the program will use the default value of 1.0


----------------
Conception
----------------

PGC is divided in two parts : the sorting part, and the actual pivot graph construction. 
Sorting will divide links according to their category and conf value into sure links and links to validate manually. 
	- Triangles, inferred triangles, single links with a conf value higher than threshold will end up in the pivot graph.
	- Conflicts, multiple links, two-links without inferrence, other single links will be written on a new alignment file, to be validated manually.

It will then proceed to build the Pivot Graph from the new sure links. At that point, only triangles and single links should be present. It is however still possible to detect abnormal patterns (such as multiple links with a conf value equal to 1.0). These links will be removed from their sure link list, and rewritten along with the links to validate. They will also be written on another file, in order to help the user to easily track them and solve the issue. 


----------------
Output Files : 
----------------

	- surelinks.rdf  : file containing all sure links found
	- tovalidate.rdf : file containing all links that have to be manually validated
	- pivotgraph.rdf : the actual pivot graph, linking created uris to their equivalent in different bases using sameAs properties
	- VSC.rdf        : "very special cases", containing abnormal (conflictuous, erroneous, etc) patterns with a very high or perfect conf value. Used to help you 
