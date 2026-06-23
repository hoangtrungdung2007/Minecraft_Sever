import urllib.request
import zipfile
import os
import sys
import subprocess

def compile_plugin():
    print("=== SETUP ENV ===")
    
    # 1. Download Maven
    maven_url = "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
    maven_zip = "C:\\tools\\maven.zip"
    if not os.path.exists("C:\\tools"):
        os.makedirs("C:\\tools")
        
    if not os.path.exists(maven_zip):
        print("Downloading Maven...")
        try:
            urllib.request.urlretrieve(maven_url, maven_zip)
            print("Maven downloaded.")
        except Exception as e:
            print("Error downloading Maven:", e)
            return

    # 2. Extract Maven
    maven_dir = "C:\\tools\\maven"
    if not os.path.exists(maven_dir):
        print("Extracting Maven...")
        try:
            with zipfile.ZipFile(maven_zip, 'r') as zip_ref:
                zip_ref.extractall(maven_dir)
            print("Maven extracted.")
        except Exception as e:
            print("Error extracting Maven:", e)
            return
            
    # 3. Extract JDK 21
    jdk_dir = "C:\\tools\\jdk21"
    if not os.path.exists(jdk_dir):
        print("Extracting JDK 21...")
        try:
            with zipfile.ZipFile("C:\\tools\\jdk21.zip", 'r') as zip_ref:
                zip_ref.extractall(jdk_dir)
            print("JDK extracted.")
        except Exception as e:
            print("Error extracting JDK:", e)
            return

    # Find JDK and Maven bin paths
    jdk_bin = ""
    for root, dirs, files in os.walk(jdk_dir):
        if "javac.exe" in files:
            jdk_bin = root
            break
            
    mvn_bin = ""
    for root, dirs, files in os.walk(maven_dir):
        if "mvn.cmd" in files:
            mvn_bin = root
            break

    if not jdk_bin or not mvn_bin:
        print("Could not find JDK or Maven bin directories.")
        return

    print("JDK Bin:", jdk_bin)
    print("Maven Bin:", mvn_bin)
    
    print("\n=== COMPILING PLUGIN ===")
    os.environ["JAVA_HOME"] = os.path.dirname(jdk_bin)
    os.environ["PATH"] = f"{jdk_bin};{mvn_bin};" + os.environ.get("PATH", "")
    
    plugin_src_dir = os.path.join(os.getcwd(), "plugins", "UtilityTools-src")
    
    try:
        process = subprocess.run(["mvn.cmd", "clean", "package", "-q"], cwd=plugin_src_dir, capture_output=True, text=True)
        if process.returncode == 0:
            print("Compilation SUCCESS!")
            
            # Copy jar
            source_jar = os.path.join(plugin_src_dir, "target", "UtilityTools-1.0.0.jar")
            dest_jar = os.path.join(os.getcwd(), "plugins", "UtilityTools-1.0.0.jar")
            
            import shutil
            shutil.copy2(source_jar, dest_jar)
            print("Plugin copied to plugins directory successfully!")
        else:
            print("Compilation FAILED!")
            print("STDOUT:", process.stdout)
            print("STDERR:", process.stderr)
    except Exception as e:
        print("Error during compilation:", e)

if __name__ == "__main__":
    compile_plugin()
