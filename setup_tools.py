import urllib.request
import zipfile
import os
import sys

def setup():
    maven_url = "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
    maven_zip = "C:\\tools\\maven.zip"
    
    if not os.path.exists(maven_zip):
        print("Downloading Maven...")
        try:
            urllib.request.urlretrieve(maven_url, maven_zip)
            print("Download complete.")
        except Exception as e:
            print("Error downloading Maven:", e)
            return
    else:
        print("Maven zip already exists.")

    print("Extracting Maven...")
    try:
        with zipfile.ZipFile(maven_zip, 'r') as zip_ref:
            zip_ref.extractall("C:\\tools\\maven")
        print("Maven extraction complete.")
    except Exception as e:
        print("Error extracting Maven:", e)

    print("Extracting JDK 21...")
    try:
        if not os.path.exists("C:\\tools\\jdk21\\jdk-21.0.7+6"):
            with zipfile.ZipFile("C:\\tools\\jdk21.zip", 'r') as zip_ref:
                zip_ref.extractall("C:\\tools\\jdk21")
            print("JDK extraction complete.")
        else:
            print("JDK already extracted.")
    except Exception as e:
        print("Error extracting JDK:", e)

if __name__ == "__main__":
    setup()
