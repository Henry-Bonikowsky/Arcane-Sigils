#!/usr/bin/env python3
"""Deploy ArcaneSigils to GravelHost via SFTP"""
import os
import sys
import glob
import argparse
import paramiko
from dotenv import load_dotenv

# Load .env file if it exists
load_dotenv()

# Server configurations
SERVERS = {
    'dev': {
        'host': 'dedicatedny.gravelhost.com',
        'port': 2022,
        'user': 'henrybonikowsky6cm.6ad75f72',
        'password_env': 'SFTP_PASS_DEV',
        'remote_path': './plugins/'
    },
    'main': {
        'host': 'dedicatedny.gravelhost.com',
        'port': 2022,
        'user': 'henrybonikowsky6cm.b4f712e6',
        'password_env': 'SFTP_PASS_MAIN',
        'remote_path': './plugins/'
    }
}

def get_sftp(server='dev'):
    config = SERVERS.get(server)
    if not config:
        print(f"Error: Unknown server '{server}'. Use 'dev' or 'main'.")
        sys.exit(1)
    
    password = os.environ.get(config['password_env'])
    if not password:
        print(f"Error: Set {config['password_env']} in .env file")
        sys.exit(1)
    
    transport = paramiko.Transport((config['host'], config['port']))
    transport.connect(username=config['user'], password=password)
    return paramiko.SFTPClient.from_transport(transport), transport, config

def cmd_deploy(args):
    """Upload latest JAR and YAMLs, always delete old JARs first"""
    server = args.server
    jars = [j for j in glob.glob("target/ArcaneSigils-*.jar") if "original" not in j]
    if not jars:
        print("No JAR found in target/. Build first.")
        sys.exit(1)

    jar = max(jars, key=os.path.getmtime)
    jar_name = os.path.basename(jar)
    
    sftp, transport, config = get_sftp(server)
    remote_path = config['remote_path']
    remote_jar_path = remote_path + jar_name

    print(f"Deploying to: {server.upper()} server")
    print(f"File: {jar}")
    print(f"To: sftp://{config['host']}:{config['port']}{remote_jar_path}")
    print()
    try:
        # ALWAYS delete old JARs first to avoid running stale versions
        print("Cleaning old JARs...")
        for f in sftp.listdir(remote_path):
            if f.startswith("ArcaneSigils-") and f.endswith(".jar") and f != jar_name:
                print(f"  Deleting: {f}")
                sftp.remove(remote_path + f)

        sftp.put(jar, remote_jar_path)
        print("JAR uploaded successfully!")

        # Deploy YAML files from src/main/resources/
        print("\nDeploying YAML files...")
        plugin_data_path = "./plugins/ArcaneSigils/"
        
        # Create remote directories if they don't exist
        for subdir in ['sigils', 'behaviors', 'marks']:
            try:
                sftp.mkdir(plugin_data_path + subdir)
            except IOError:
                pass  # Directory already exists
        
        # Upload all YAMLs from each directory
        for subdir in ['sigils', 'behaviors', 'marks']:
            local_dir = f"src/main/resources/{subdir}/"
            if os.path.exists(local_dir):
                for yaml_file in glob.glob(local_dir + "*.yml"):
                    filename = os.path.basename(yaml_file)
                    remote_yaml_path = f"{plugin_data_path}{subdir}/{filename}"
                    sftp.put(yaml_file, remote_yaml_path)
                    print(f"  Uploaded: {subdir}/{filename}")
        
        print("\nDeploy complete! Restart server to load changes.")
    finally:
        sftp.close()
        transport.close()

def cmd_ls(args):
    """List files on server"""
    sftp, transport, config = get_sftp(args.server)
    path = args.path or config['remote_path']
    if not path.endswith('/'):
        path += '/'
    print(f"Listing: {args.server.upper()} server - {path}\n")
    try:
        for attr in sftp.listdir_attr(path):
            ftype = "d" if attr.st_mode & 0o40000 else "-"
            print(f"{ftype} {attr.st_size:>10}  {attr.filename}")
    finally:
        sftp.close()
        transport.close()

def cmd_cat(args):
    """Read file from server"""
    sftp, transport, config = get_sftp(args.server)
    try:
        with sftp.open(args.path, 'r') as f:
            print(f.read().decode('utf-8'))
    finally:
        sftp.close()
        transport.close()

def cmd_pull(args):
    """Download file from server to local"""
    sftp, transport, config = get_sftp(args.server)
    try:
        local_path = args.local or os.path.basename(args.remote)
        sftp.get(args.remote, local_path)
        print(f"Downloaded: {args.remote} -> {local_path}")
    finally:
        sftp.close()
        transport.close()

def cmd_push(args):
    """Upload file to server"""
    sftp, transport, config = get_sftp(args.server)
    try:
        remote_path = args.remote or config['remote_path'] + os.path.basename(args.local)
        sftp.put(args.local, remote_path)
        print(f"Uploaded: {args.local} -> {remote_path}")
    finally:
        sftp.close()
        transport.close()

def cmd_rm(args):
    """Delete file on server"""
    sftp, transport, config = get_sftp(args.server)
    try:
        sftp.remove(args.path)
        print(f"Deleted: {args.path}")
    finally:
        sftp.close()
        transport.close()

def main():
    parser = argparse.ArgumentParser(description="GravelHost SFTP tool")
    sub = parser.add_subparsers(dest="cmd")

    p_deploy = sub.add_parser("deploy", help="Upload latest JAR (auto-deletes old JARs)")
    p_deploy.add_argument('--server', '-s', choices=['dev', 'main'], default='main',
                          help='Target server (default: main)')

    p_ls = sub.add_parser("ls", help="List remote directory")
    p_ls.add_argument("path", nargs="?", help="Remote path (default: modules dir)")
    p_ls.add_argument('--server', '-s', choices=['dev', 'main'], default='main',
                      help='Target server (default: main)')

    p_cat = sub.add_parser("cat", help="Read remote file")
    p_cat.add_argument("path", help="Remote file path")
    p_cat.add_argument('--server', '-s', choices=['dev', 'main'], default='main',
                       help='Target server (default: main)')

    p_pull = sub.add_parser("pull", help="Download file from server")
    p_pull.add_argument("remote", help="Remote file path")
    p_pull.add_argument("local", nargs="?", help="Local path (default: same name)")
    p_pull.add_argument('--server', '-s', choices=['dev', 'main'], default='main',
                        help='Target server (default: main)')

    p_push = sub.add_parser("push", help="Upload file to server")
    p_push.add_argument("local", help="Local file path")
    p_push.add_argument("remote", nargs="?", help="Remote path (default: modules dir)")
    p_push.add_argument('--server', '-s', choices=['dev', 'main'], default='main',
                        help='Target server (default: main)')

    p_rm = sub.add_parser("rm", help="Delete remote file")
    p_rm.add_argument("path", help="Remote file path")
    p_rm.add_argument('--server', '-s', choices=['dev', 'main'], default='main',
                      help='Target server (default: main)')

    args = parser.parse_args()

    if args.cmd == "deploy" or args.cmd is None:
        cmd_deploy(args)
    elif args.cmd == "ls":
        cmd_ls(args)
    elif args.cmd == "cat":
        cmd_cat(args)
    elif args.cmd == "pull":
        cmd_pull(args)
    elif args.cmd == "push":
        cmd_push(args)
    elif args.cmd == "rm":
        cmd_rm(args)

if __name__ == "__main__":
    main()
