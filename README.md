read-benchmark
==============

A quick and dirty random read disk benchmark

Benchmark consists of random 1k aligned reads across a 64 gigabyte preallocated data file. Caches are dropped in between runs. The runs are 600 seconds long with a 600 second warmup. I checked and the 600 second warmup is long enough to heat up the caches of in-memory workloads even with the 7.2k disk.

Read ahead on the SSD was disabled at both the kernel and device level using blockdev and hdparm otherwise you don't get 33k random reads. Read ahead of the disk was not disabled and left at the default of 256 blocks (128 kilobytes). Playing with read ahead for the disk might marginally improve the larger than memory workloads by preventing eviction of hot pages, but it would have made warmup take longer and it's not something I explored.

Different runs of the benchmark will use only the beginning of the file to simulate smaller data sets.

Access distribution is Zipfian with the output run through FNV hash to scramble access in order to simulate real world data sets where hot and cold data are mixed together over time on shared disk pages. It's a little pessimistic, but not out of line for many use cases

Files are memory mapped and the bytes pulled out of the file are checksummed with XOR to prevent dead code elimination. The overhead of the checksum is minimal and many gigabytes a second a processed when the dataset is in memory.

Benchmark hardware:
SSD 
Crucial m4 128 gigabyte showing 33k random reads/sec
16 gigabytes RAM
Desktop class Sandy Bridge quad-core i5 65w

Disk
Seagate 3 terabyte 7.2k drive ST3000DM001 showing up to 250 random reads/sec
16 gigabytes RAM
Desktop class Ivy Bridge quad-core i7 65w

Ubuntu 12.04 on both systems running ext4 mounted with noatime,nodiratime
