FROM nginx:latest

# Remove the default Nginx configuration file
RUN rm -v /etc/nginx/nginx.conf

# Copy a configuration file from the current directory
COPY /etc/nginx/nginx.conf /etc/nginx/

COPY ./etc/webapp /var/www/html/
