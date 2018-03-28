# pivot-graph-constructor
Algorithm for creating the pivot graph of works out of the interlinking process of DOREMUS datasets

The algorithm creates a graph that contains the union of all works (entities from the F22 class) contained in the DOREMUS bases, identified by nouvel URIs and linked via owl:sameAs statemets to their correpsonding works in each base. When a link between two works across two bases has been established in the data linking process (see https://github.com/DOREMUS-ANR/knowledge-base/tree/master/linked-data), this link is also added to the pivot graph. 
