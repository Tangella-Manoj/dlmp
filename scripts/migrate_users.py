import os
import sys
import uuid
import subprocess
from datetime import datetime

# ─── AUTO-INSTALL DEPENDENCIES ───────────────────────────────────────────────
def check_dependencies():
    """Checks and automatically installs required packages if missing."""
    required = {
        "sqlalchemy": "SQLAlchemy",
        "pymysql": "pymysql",
        "psycopg2": "psycopg2-binary"
    }
    missing = []
    for module, package in required.items():
        try:
            __import__(module)
        except ImportError:
            missing.append(package)
            
    if missing:
        print(f"Required packages {missing} are missing. Installing them automatically...")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install", *missing])
            print("Packages installed successfully.\n")
        except Exception as e:
            print(f"❌ Failed to automatically install dependencies: {e}")
            print(f"Please run manually: pip install {' '.join(missing)}")
            sys.exit(1)

# ─── DYNAMIC CONFIGURATION PARSING ──────────────────────────────────────────
def get_openbravo_db_url():
    """Parses Openbravo.properties to dynamically resolve PostgreSQL details."""
    paths = [
        "/opt/sairoshni/config/Openbravo.properties",
        "./config/Openbravo.properties",
        "../config/Openbravo.properties",
        "../../config/Openbravo.properties"
    ]
    for path in paths:
        if os.path.exists(path):
            try:
                config = {}
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#"):
                            parts = line.split("=", 1)
                            if len(parts) == 2:
                                config[parts[0].strip()] = parts[1].strip()
                
                if config.get("bbdd.rdbms") == "POSTGRE":
                    jdbc_url = config.get("bbdd.url", "jdbc:postgresql://localhost:5432")
                    host_port = jdbc_url.replace("jdbc:postgresql://", "").strip()
                    if not host_port:
                        host_port = "localhost:5432"
                    
                    db_name = config.get("bbdd.sid", "local")
                    user = config.get("bbdd.user", "tad")
                    password = config.get("bbdd.password", "tad")
                    
                    print(f"✅ Auto-detected Openbravo PostgreSQL credentials from: {path}")
                    return f"postgresql://{user}:{password}@{host_port}/{db_name}"
            except Exception as e:
                print(f"⚠️ Error parsing Openbravo.properties at {path}: {e}")
                
    # Fallback to defaults from Openbravo.properties
    return "postgresql://tad:tad@localhost:5432/local"

def get_target_db_url():
    """Resolves target MySQL database credentials from .env or application.yml."""
    # Load .env if present
    env_paths = [".env", "../.env", "../../.env", "./distributed-loan-management-platform/.env"]
    for path in env_paths:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#"):
                            parts = line.split("=", 1)
                            if len(parts) == 2:
                                os.environ[parts[0].strip()] = parts[1].strip()
                print(f"✅ Loaded environment variables from: {path}")
                break
            except Exception as e:
                print(f"⚠️ Error loading env from {path}: {e}")
                
    # Check Environment Variables
    mysql_user = os.getenv("MYSQL_USER")
    mysql_pass = os.getenv("MYSQL_PASSWORD")
    mysql_host = os.getenv("MYSQL_HOST")
    mysql_port = os.getenv("MYSQL_PORT", "3306")
    mysql_db = os.getenv("MYSQL_DB_NAME", "dlmp_users")
    
    # If variables are missing, try parsing Spring Boot user-service config
    if not mysql_user or not mysql_pass:
        yml_paths = [
            "user-service/src/main/resources/application.yml",
            "../user-service/src/main/resources/application.yml",
            "./user-service/src/main/resources/application.yml"
        ]
        for path in yml_paths:
            if os.path.exists(path):
                try:
                    import re
                    with open(path, "r", encoding="utf-8") as f:
                        content = f.read()
                    
                    user_match = re.search(r"username:\s*\${MYSQL_USER:([^}]+)}", content)
                    pass_match = re.search(r"password:\s*\${MYSQL_PASSWORD:([^}]+)}", content)
                    
                    if user_match:
                        mysql_user = user_match.group(1).strip()
                    if pass_match:
                        mysql_pass = pass_match.group(1).strip()
                    
                    print(f"✅ Auto-detected User Service MySQL default credentials from: {path}")
                    break
                except Exception as e:
                    print(f"⚠️ Error reading {path}: {e}")

    # Final defaults if still missing
    mysql_user = mysql_user or "dlmp_user"
    mysql_pass = mysql_pass or "dlmp_password"
    mysql_host = mysql_host or "localhost"
    
    # If host is docker service name and we run locally, map it to localhost
    if mysql_host == "mysql-users":
        mysql_host = "localhost"
        
    return f"mysql+pymysql://{mysql_user}:{mysql_pass}@{mysql_host}:{mysql_port}/{mysql_db}"

# ─── UTILITIES ───────────────────────────────────────────────────────────────
def format_to_uuid(hex_id):
    """Converts a 32-character hex ID to a standard 36-character hyphenated UUID."""
    if not hex_id:
        return str(uuid.uuid4())
    hex_clean = hex_id.strip()
    if len(hex_clean) == 32:
        try:
            return str(uuid.UUID(hex_clean))
        except ValueError:
            return hex_clean
    return hex_clean

# ─── MAIN MIGRATION FLOW ──────────────────────────────────────────────────────
def main():
    check_dependencies()
    
    from sqlalchemy import create_engine, text, inspect
    
    src_url = get_openbravo_db_url()
    dest_url = get_target_db_url()
    
    print(f"Connecting to Source DB: {src_url.split('@')[-1]} (PostgreSQL)...")
    print(f"Connecting to Target DB: {dest_url.split('@')[-1]} (MySQL)...")
    
    try:
        src_engine = create_engine(src_url)
        dest_engine = create_engine(dest_url)
        
        # Test connections
        with src_engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        with dest_engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        print("✅ Database connections established successfully.")
    except Exception as e:
        print(f"❌ Connection error: {e}")
        print("\nEnsure that both PostgreSQL (Openbravo) and MySQL (DLMP) instances are running.")
        sys.exit(1)

    # Automatically detect target schema strategy (Strategy A vs B)
    try:
        inspector = inspect(dest_engine)
        columns = [c['name'] for c in inspector.get_columns('users')]
        has_username_col = 'username' in columns
        print(f"⚙️ Schema Detection: 'username' column {'EXISTS' if has_username_col else 'DOES NOT EXIST'} in target 'users' table.")
        print(f"👉 Automatically using Strategy {'B (Schema Extended)' if has_username_col else 'A (Email Fallback/No Schema Changes)'}.")
    except Exception as e:
        print(f"❌ Error inspecting target 'users' table: {e}")
        print("Please ensure the Flyway migrations for user-service have run to initialize the schema.")
        sys.exit(1)

    # Fetch users from legacy ad_user table
    query = text("""
        SELECT 
            ad_user_id, username, name, firstname, lastname, 
            email, password, phone, phone2, em_lds_pan, 
            em_lds_usertype, isactive, created, updated 
        FROM ad_user
    """)
    
    print("Reading data from legacy 'ad_user' table...")
    try:
        with src_engine.connect() as src_conn:
            result = src_conn.execute(query)
            users = [dict(row._mapping) for row in result.fetchall()]
        print(f"Loaded {len(users)} users from legacy Openbravo DB.")
    except Exception as e:
        print(f"❌ Error loading users from legacy database: {e}")
        sys.exit(1)

    # Set up insert statement
    if has_username_col:
        insert_sql = text("""
            INSERT INTO users (
                id, first_name, last_name, email, username, password_hash, 
                phone_number, pan_number, role, status, created_at, updated_at
            ) VALUES (
                :id, :first_name, :last_name, :email, :username, :password_hash, 
                :phone_number, :pan_number, :role, :status, :created_at, :updated_at
            ) ON DUPLICATE KEY UPDATE 
                first_name = VALUES(first_name),
                last_name = VALUES(last_name),
                username = VALUES(username),
                password_hash = VALUES(password_hash),
                phone_number = VALUES(phone_number),
                pan_number = VALUES(pan_number),
                role = VALUES(role),
                status = VALUES(status),
                updated_at = NOW()
        """)
    else:
        insert_sql = text("""
            INSERT INTO users (
                id, first_name, last_name, email, password_hash, 
                phone_number, pan_number, role, status, created_at, updated_at
            ) VALUES (
                :id, :first_name, :last_name, :email, :password_hash, 
                :phone_number, :pan_number, :role, :status, :created_at, :updated_at
            ) ON DUPLICATE KEY UPDATE 
                first_name = VALUES(first_name),
                last_name = VALUES(last_name),
                password_hash = VALUES(password_hash),
                phone_number = VALUES(phone_number),
                pan_number = VALUES(pan_number),
                role = VALUES(role),
                status = VALUES(status),
                updated_at = NOW()
        """)

    migrated_count = 0
    warning_passwords = 0
    now = datetime.now()

    print("Migrating records to new 'users' table...")
    with dest_engine.begin() as dest_conn:
        for u in users:
            ad_user_id = u.get('ad_user_id')
            raw_username = u.get('username').strip() if u.get('username') else None
            
            if not raw_username:
                raw_username = f"user_{ad_user_id[:6]}" if ad_user_id else "user_unknown"
                
            raw_email = u.get('email').strip() if u.get('email') else ""
            email_val = raw_email if raw_email else f"{raw_username.lower()}@dlmp.local"
            
            raw_name = u.get('name').strip() if u.get('name') else ""
            first_name = u.get('firstname').strip() if u.get('firstname') else (raw_name.split(" ")[0] if raw_name else "System")
            last_name = u.get('lastname').strip() if u.get('lastname') else (" ".join(raw_name.split(" ")[1:]) if " " in raw_name else "N/A")
            
            if not first_name:
                first_name = "System"
            if not last_name:
                last_name = "N/A"

            usertype = u.get('em_lds_usertype')
            if usertype == 'ADMIN':
                role = 'ROLE_ADMIN'
            elif usertype == 'STAFF':
                role = 'ROLE_EMPLOYEE'
            else:
                role = 'ROLE_CUSTOMER'
            
            status = 'ACTIVE' if u.get('isactive') == 'Y' else 'INACTIVE'
            user_uuid = format_to_uuid(ad_user_id)
            
            pwd = u.get('password')
            # Check if password is not BCrypt-hashed (doesn't start with $2a$ or $2b$)
            if not pwd or not pwd.startswith('$2'):
                warning_passwords += 1
                # Reset to default BCrypt hash for testing: 'Admin@2026'
                pwd = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj6uk3GaJwwu"
            
            values = {
                "id": user_uuid,
                "first_name": first_name[:50],
                "last_name": last_name[:50],
                "email": email_val.lower()[:100],
                "password_hash": pwd,
                "phone_number": (u.get('phone') or u.get('phone2') or "0000000000").strip()[:15],
                "pan_number": u.get('em_lds_pan').strip()[:10] if u.get('em_lds_pan') else None,
                "role": role,
                "status": status,
                "created_at": u.get('created') or now,
                "updated_at": u.get('updated') or now
            }
            
            if has_username_col:
                values["username"] = raw_username.lower()[:50]
                
            dest_conn.execute(insert_sql, values)
            migrated_count += 1
            
    print(f"\n🎉 Migration completed successfully! Migrated {migrated_count} users.")
    if warning_passwords > 0:
        print(f"⚠️ Reset {warning_passwords} users with empty/legacy passwords to test default: 'Admin@2026'.")

if __name__ == "__main__":
    main()
