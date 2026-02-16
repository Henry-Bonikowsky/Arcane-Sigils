#!/usr/bin/env python3
"""Fetch sigil files from GravelHost server for debugging"""
import os
import sys
import paramiko
from dotenv import load_dotenv

# Load .env file if it exists
load_dotenv()

HOST = "dedicatedny.gravelhost.com"
PORT = 2022
USER = "henrybonikowsky6cm.b4f712e6"
REMOTE_PLUGIN_PATH = "./plugins/ArcaneSigils/"

def get_sftp():
    password = os.environ.get("SFTP_PASS")
    if not password:
        print("Error: Set SFTP_PASS environment variable or create .env file")
        sys.exit(1)
    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=password)
    return paramiko.SFTPClient.from_transport(transport), transport

def fetch_sigils():
    """Download all sigil files from server to local directory"""
    sftp, transport = get_sftp()
    
    try:
        # Create local directory to store server files
        local_dir = "server-files/sigils/"
        os.makedirs(local_dir, exist_ok=True)
        
        remote_sigils_path = REMOTE_PLUGIN_PATH + "sigils/"
        
        print(f"Fetching sigils from: {remote_sigils_path}")
        print(f"Saving to: {local_dir}")
        print()
        
        # List all files in the sigils directory
        try:
            files = sftp.listdir(remote_sigils_path)
        except IOError:
            print(f"Error: Could not access {remote_sigils_path}")
            print("Make sure the plugin is deployed and the path is correct.")
            return
        
        # Download each .yml file
        yml_files = [f for f in files if f.endswith('.yml')]
        
        if not yml_files:
            print("No .yml files found in sigils directory!")
            return
        
        for filename in yml_files:
            remote_file = remote_sigils_path + filename
            local_file = local_dir + filename
            
            try:
                sftp.get(remote_file, local_file)
                print(f"[OK] Downloaded: {filename}")
            except Exception as e:
                print(f"[FAIL] Failed to download {filename}: {e}")
        
        print()
        print(f"Downloaded {len(yml_files)} sigil file(s)!")
        print(f"Files saved to: {os.path.abspath(local_dir)}")
        
    finally:
        sftp.close()
        transport.close()

def fetch_config():
    """Download config.yml from server"""
    sftp, transport = get_sftp()
    
    try:
        local_dir = "server-files/"
        os.makedirs(local_dir, exist_ok=True)
        
        remote_config = REMOTE_PLUGIN_PATH + "config.yml"
        local_config = local_dir + "config.yml"
        
        print(f"Fetching config from: {remote_config}")
        
        try:
            sftp.get(remote_config, local_config)
            print(f"[OK] Downloaded config.yml")
            print(f"Saved to: {os.path.abspath(local_config)}")
        except Exception as e:
            print(f"[FAIL] Failed to download config.yml: {e}")
        
    finally:
        sftp.close()
        transport.close()

def list_server_files():
    """List all files in the ArcaneSigils plugin directory"""
    sftp, transport = get_sftp()
    
    try:
        print(f"Listing files in: {REMOTE_PLUGIN_PATH}")
        print()
        
        def list_recursive(path, indent=0):
            try:
                attrs = sftp.listdir_attr(path)
                for attr in attrs:
                    prefix = "  " * indent
                    if attr.st_mode & 0o40000:  # Directory
                        print(f"{prefix}[DIR]  {attr.filename}/")
                        list_recursive(path + attr.filename + "/", indent + 1)
                    else:
                        size_kb = attr.st_size / 1024
                        print(f"{prefix}[FILE] {attr.filename} ({size_kb:.1f} KB)")
            except Exception as e:
                print(f"{prefix}[ERROR] Error reading {path}: {e}")
        
        list_recursive(REMOTE_PLUGIN_PATH)
        
    finally:
        sftp.close()
        transport.close()

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Fetch ArcaneSigils files from server")
    parser.add_argument("command", choices=["sigils", "config", "list", "all"], 
                       help="What to fetch: sigils (sigil YMLs), config (config.yml), list (show files), all (sigils+config)")
    
    args = parser.parse_args()
    
    if args.command == "sigils":
        fetch_sigils()
    elif args.command == "config":
        fetch_config()
    elif args.command == "list":
        list_server_files()
    elif args.command == "all":
        fetch_sigils()
        print()
        fetch_config()

if __name__ == "__main__":
    main()
