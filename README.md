# Lottery

Provide users with a simple and interesting lottery experience. Administrators can flexibly manage prizes and set lottery rules, while users can participate in the lottery and enjoy winning. The system provides data statistics and analysis functions to ensure the security and stability of the system.



## Admin page

![在这里插入图片描述](https://img-blog.csdnimg.cn/direct/9372bab8e8b34e42a8accd064d9447ec.png)



## User page

![在这里插入图片描述](https://img-blog.csdnimg.cn/direct/917cd09381fc4d12b415773317b6baf2.png)



## Installation and Config (Linux)

### Install Mysql

```sh
docker run \
--name mysql \
-e MYSQL_ROOT_PASSWORD=root \
-p 3306:3306 \
-v /usr/local/mysql/conf/hmy.cnf:/etc/mysql/conf.d/hmy.cnf \
-v /usr/local/mysql/data:/var/lib/mysql \
-d \
mysql:5.7.25
```



### Install Redis

```sh
docker run \
--name prize-redis \
-p 6379:6379 -d\
daocloud.io/library/redis:6.0.6
```



### Install Rabbitmq

```sh
docker run \
 -e RABBITMQ_DEFAULT_USER=root \
 -e RABBITMQ_DEFAULT_PASS=root \
 -v mq-plugins:/plugins \
 --name mq \
 --hostname mq1 \
 -p 15672:15672 \
 -p 5672:5672 \
 -d \
 rabbitmq:3.8-management
```



### Install  Minio

```sh
docker run -p9005:9000 -p9006:9090 \
--name prize-minio -d \
-e "MINIO_ACCESS_KEY=minioadmin" \
-e "MINIO_SECRET_KEY=minioadmin" \
-v /opt/data/minio/data:/data \
-v /opt/data/minio/config:/root/.minio \
minio/minio:RELEASE.2023-12-02T10-51-33Z  \
 server /data --console-address ":9090" --address ":9000"
```



### Config Ip/port

**Config backend Ip address**

- application.yml
- application-druid.yml
- http://localhost:8888

![在这里插入图片描述](https://img-blog.csdnimg.cn/direct/c1320bc75e9d4592aa2a983bb65406f6.png#pic_center)



**Config frontend Ip address**

- api/appication.properties
- msg/appication.properties
- Swagger: http://localhost:9001/doc.html 
- user page: http://localhost

![在这里插入图片描述](https://img-blog.csdnimg.cn/direct/bc101d2fae6844f0ab51e99ab4939e3c.png#pic_center)



### Deploy Nginx or Openresty

```sh
# DownloadOpenresty, address: http://openresty.org/en/download.html
# After decompression, overwrite nginx.conf in the root folder of the lottery project with conf/nginx.conf in openresty
# Start openresty
```



![在这里插入图片描述](https://img-blog.csdnimg.cn/direct/540a9d081dd34227ae8cd3a94f155260.png#pic_center)