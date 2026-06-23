import zipfile
import os

def extract_jdk():
    print("Extracting JDK 21...")
    try:
        with zipfile.ZipFile("C:\\tools\\jdk21.zip", 'r') as zip_ref:
            zip_ref.extractall("C:\\tools\\jdk21")
        print("JDK extraction complete.")
    except Exception as e:
        print("Error extracting JDK:", e)

if __name__ == "__main__":
    extract_jdk()
