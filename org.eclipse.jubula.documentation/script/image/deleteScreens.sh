#*******************************************************************************
# Copyright (c) 2004, 2010 BREDEX GmbH.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     BREDEX GmbH - initial API and implementation and/or initial documentation
#*******************************************************************************
#!/bin/bash

echo "Removing files"

cd ../..

listOfFiles=`find . -name "*.jpg" | sed -e s/.jpg//`
for i in $listOfFiles; do rm -rf $i.jpg; done

listOfFiles=`find . -name "*.eps"`
for i in $listOfFiles; do rm -rf $i; done

cd ..