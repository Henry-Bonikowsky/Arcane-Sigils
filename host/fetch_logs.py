#!/usr/bin/env python3
"""Fetch debug log files from ArcaneSigils server"""
import os
import sys
import argparse
import paramiko
from dotenv import load_dotenv
from datetime import datetime

# Load .env file
load_dotenv()

# Server configurations
SERVERS = {
    'dev': {
        'host': 'dedicatedny.gravelhost.com',
        'port': 2022,
        'user': 'henrybonikowsky6cm.6ad75f72',
        'password_env': 'SFTP_PASS_DEV',
        'logs_path': './plugins/ArcaneSigils/logs/'
    },
    'main': {
        'host': 'dedicatedny.gravelhost.com',
        'port': 2022,
        'user': 'henrybonikowsky6cm.b4f712e6',
        'password_env': 'SFTP_PASS_MAIN',
        'logs_path': './plugins/ArcaneSigils/logs/'
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

def list_logs(server='dev'):
    """List all available log files on the server"""
    sftp, transport, config = get_sftp(server)
    logs_path = config['logs_path']

    try:
        files = sftp.listdir(logs_path)
        log_files = [f for f in files if f.endswith('.log')]

        if not log_files:
            print(f"No log files found in {logs_path}")
            return

        print(f"\n{server.upper()} Server - Available log files:")
        print("-" * 60)

        # Sort by name (current debug.log first, then archives by date)
        log_files.sort(reverse=True)
        current = [f for f in log_files if f == 'debug.log']
        archives = [f for f in log_files if f.startswith('debug-')]

        for f in current + archives:
            attrs = sftp.stat(logs_path + f)
            size_kb = attrs.st_size / 1024
            mtime = datetime.fromtimestamp(attrs.st_mtime).strftime('%Y-%m-%d %H:%M:%S')
            status = "(CURRENT)" if f == 'debug.log' else ""
            print(f"  {f:40} {size_kb:8.1f} KB  {mtime}  {status}")

        print("-" * 60)

    except Exception as e:
        print(f"Error listing logs: {e}")
    finally:
        transport.close()

def download_log(filename, server='dev', output_dir='logs'):
    """Download a specific log file"""
    sftp, transport, config = get_sftp(server)
    logs_path = config['logs_path']
    remote_file = logs_path + filename

    # Create local output directory
    os.makedirs(output_dir, exist_ok=True)

    # Add server prefix to local filename to avoid confusion
    local_filename = f"{server}-{filename}"
    local_file = os.path.join(output_dir, local_filename)

    try:
        print(f"Downloading: {filename}")
        print(f"From: sftp://{config['host']}:{config['port']}{remote_file}")
        print(f"To: {local_file}")

        sftp.get(remote_file, local_file)

        file_size = os.path.getsize(local_file)
        print(f"\nDownloaded successfully! ({file_size / 1024:.1f} KB)")
        print(f"Saved to: {local_file}")

    except FileNotFoundError:
        print(f"Error: File '{filename}' not found on server")
        print(f"Use 'python fetch_logs.py list' to see available files")
    except Exception as e:
        print(f"Error downloading log: {e}")
    finally:
        transport.close()

def download_latest(server='dev', output_dir='logs'):
    """Download the current debug.log file"""
    download_log('debug.log', server, output_dir)

def download_all(server='dev', output_dir='logs'):
    """Download all log files from the server"""
    sftp, transport, config = get_sftp(server)
    logs_path = config['logs_path']

    try:
        files = sftp.listdir(logs_path)
        log_files = [f for f in files if f.endswith('.log')]

        if not log_files:
            print(f"No log files found on {server} server")
            return

        os.makedirs(output_dir, exist_ok=True)

        print(f"Downloading {len(log_files)} log files from {server.upper()} server...")
        print()

        for filename in log_files:
            remote_file = logs_path + filename
            local_filename = f"{server}-{filename}"
            local_file = os.path.join(output_dir, local_filename)

            sftp.get(remote_file, local_file)
            file_size = os.path.getsize(local_file)
            print(f"  * {filename:40} ({file_size / 1024:.1f} KB)")

        print(f"\nAll logs downloaded to: {output_dir}/")

    except Exception as e:
        print(f"Error downloading logs: {e}")
    finally:
        transport.close()

def main():
    parser = argparse.ArgumentParser(description='Fetch debug logs from ArcaneSigils server')
    parser.add_argument('command', nargs='?', default='latest',
                       choices=['latest', 'list', 'all', 'get'],
                       help='Command: latest (current log), list (show all), all (download all), get (specific file)')
    parser.add_argument('filename', nargs='?',
                       help='Filename to download (for "get" command)')
    parser.add_argument('-s', '--server', default='dev', choices=['dev', 'main'],
                       help='Server to connect to (default: dev)')
    parser.add_argument('-o', '--output', default='logs',
                       help='Output directory for downloaded logs (default: logs/)')

    args = parser.parse_args()

    if args.command == 'list':
        list_logs(args.server)

    elif args.command == 'latest':
        download_latest(args.server, args.output)

    elif args.command == 'all':
        download_all(args.server, args.output)

    elif args.command == 'get':
        if not args.filename:
            print("Error: Specify a filename to download")
            print("Example: python fetch_logs.py get debug-2026-01-21-16-30.log")
            sys.exit(1)
        download_log(args.filename, args.server, args.output)

if __name__ == '__main__':
    main()
