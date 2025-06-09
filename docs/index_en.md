---
title: Data import without catalogue query for ETH Zurich
identifier: intranda_import_eth_no_catalogue
description: This import plugin for Goobi workflow allows data to be imported without a catalogue query, as is required for ETH Zurich, especially for multi-volume works.
published: true
---

## Introduction
This import plugin allows data to be imported without a previous catalogue query. It inserts data into the user interface that has previously been copied from an Excel file and where the columns are separated from each other using `TAB`.

## Installation
To be able to use the plugin, the following files must be installed:

```bash
/opt/digiverso/goobi/plugins/import/plugin-import-eth-no-catalogue-base.jar
/opt/digiverso/goobi/config/plugin_intranda_import_eth_no_catalogue.xml
```

Once the plugin has been installed, it can be accessed from the overview of production templates by using the second blue button next to the selected production template.

![Production template with additional blue button for mass import](screen1_en.png)

At this point, you can choose between two different areas of the plugin. You can choose whether you want to work with library objects (`plugin_import_eth_no_catalogue`) or archival objects (`plugin_import_eth_archival_objects`).

Once the desired plugin has been entered, a user interface is available in which the data to be imported can be selected or uploaded.

![User interface of the import plugin](screen2_en.png)


## Overview and functionality
After selecting the correct plugin, the data that is either available as TAB-separated CSV data or copied from an Excel file can be inserted into the ‘Data records’ field in the user interface. The data has the following structure:

### Plugin: plugin_import_eth_no_catalogue

#### Eight columns for cards
If eight columns are used, they have the following structure:

Spalte    | Metadata       | Explanation
----------|-----------------|-------------------------
`1`       | `Identifier`    | This is the mandatory specification of the identifier.
`2`       | `Signature`      | This is the mandatory signature information.
`3`       | `Collection`      | This is the mandatory information for the collection.
`4`       | `Date`         | This is the mandatory entry of the digitisation date.
`5`       | `Units`     | This is the mandatory specification of the units.
`6`       | `Scans`         | This is the mandatory information for the scans.
`7`       | `dpi`           | This is the mandatory information for the resolution.
`8`       | `Remarks`   | This is the mandatory information with comments.


#### Four columns for monographs and multi-volume works
If four columns are used, they have the following structure:

Column    | Metadata       | Explanation
----------|-----------------|-------------------------
`1`       | `MMS-ID`        | If this contains an underscore, a multi-volume work is created, otherwise a monograph. This is a mandatory entry.
`2`       | `Signature`     | This is a mandatory entry.
`3`       | `Collection`    | Specification of the collection to be assigned. This is a mandatory entry.
`4`       | `Title`         | This is an optional specification.


### Plugin: plugin_import_eth_archival_objects

#### Two columns for archive records
If two columns are used, they have the following structure:

Spalte    | Metadata       | Explanation
----------|----------------|-------------------------
`1`       | `Shelfmark`    | This is the mandatory shelfmark information.
`2`       | `Date`         | This is the mandatory entry of the digitisation date.


#### Drei Spalten für Archivalien in Boxen
Wenn drei Spalten verwendet werden, haben diese den folgenden Aufbau:

Spalte    | Metadatum       | Erläuterung
----------|-----------------|-------------------------
`1`       | `Box`           | This is the mandatory entry of the box number.
`2`       | `Shelfmark`           | This is the mandatory entry of the shelfmark.
`3`       | `Date`          | This is the mandatory entry of the digitisation date.


Immediately after inserting the data and clicking on ‘Save’, the creation of the processes starts without a catalogue being requested.


## Configuration
The plugin is configured in the file `plugin_intranda_import_eth_no_catalogue.xml` as shown here:

{{CONFIG_CONTENT}}

The following table contains a summary of the parameters and their descriptions:

Parameter               | Explanation
------------------------|------------------------------------
`template`              | This can be used to define which production template the respective `config` block should apply to. 
`runAsGoobiScript`      | This parameter can be used to specify whether the import should take place as GoobiScript in the background.
