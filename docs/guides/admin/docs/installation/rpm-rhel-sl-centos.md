Install from Repository (RedHat Enterprise Linux, CentOS, Scientific Linux)
===========================================================================

There is an RPM software repository available for RedHat-based Linux distributions provided by the University of
Osnabrück. This repository provides preconfigured Opencast installations, including all 3rd-Party-Tools. Using this
method, you do not have to compile the software by yourself.

It may also be interesting for developers as all dependencies for Opencast usage, testing and development are provided
by the RPM repository.


Availability
------------

Note that it may take some time (usually about two weeks after a new release is out) before the RPMs are available.
Watch for announcements on list or just check which versions are available in the repository.


Currently Supported
-------------------

 - CentOS 7.x (x86\_64)
 - RedHat Enterprise Linux 7.x (x86\_64)
 - Scientific Linux 7.x (x86\_64)

> *Other architectures like i386, i686, arm, … are not supported!*


Registration
------------

Before you can start you need to get an account for the repository. You will need the credentials that you get by mail
after the registration to successfully complete this manual. The placeholders `[your_username]` and `[your_password]`
are used in this manual wherever the credentials are needed.

[Please visit https://pkg.opencast.org](https://pkg.opencast.org)


Activate Repository
-------------------

First you have to install the necessary repositories so that your package manager can access them:

 - Add Opencast repository:

        cd /etc/yum.repos.d
        curl -O https://pkg.opencast.org/opencast.repo \
           -d os=el -d version=7 -u [YOUR_USERNAME]

    You will be asked for your password.

    It might take some time after the release of a new Opencast version before the RPMs are moved to the stable
    repository. Until then, you can use `.../opencast-testing.repo` instead to get the latest version. Note that the
    testing repository is an additional repository and still requires the stable repository to be active.

2. Add EPEL repository:

        yum install epel-release


Install Apache ActiveMQ
-----------------------

The Apache ActiveMQ message broker is required by Opencast since version 2.0. It does not necessarily have to be
installed on the same machine as Opencast but would commonly for an all-in-one system. ActiveMQ is available from the
Opencast RPM repository as well and can be installed by running:

    yum install activemq-dist

A prepared configuration file for ActiveMQ can be found at `/usr/share/opencast/docs/scripts/activemq/activemq.xml`
*after Opencast itself has been installed* and should replace `/etc/activemq/activemq.xml`. For an all-in-one
installation the following command should suffice:

    cp /usr/share/opencast/docs/scripts/activemq/activemq.xml /etc/activemq/activemq.xml

ActiveMQ should be started *prior to* Opencast.

More information about how to properly set up ActiveMQ for Opencast can be found in the [message broker configuration
documentation](../configuration/message-broker.md).


Install Opencast
------------------

For this guide, `opencast22-*` is used as placeholder for the package name. It will install the latest version of the
Opencast 2.2.x branch. If you want to install another version, please change the name accordingly.


### Basic Installation

For a basic installation (All-In-One) just run:

    yum install opencast22-allinone

This will install the default distribution of Opencast and all its dependencies, including the 3rd-Party-Tools.

Now you can start Opencast:

 - On a SysV-init based system

        service opencast start

 - On a Systemd based system

        systemctl start opencast.service

While Opencast is preconfigured, it is strongly recommended to follow at least the [Basic Configuration
guide](../configuration/basic.md). It will help you to set your hostname, login information, …


Advanced Installation
---------------------

While the basic installation will give you an all-in-one Opencast distribution which is nice for testing, you might
want to have more control over your system and deploy it over several machines by choosing which parts of Opencast you
want to install. You can list all Opencast packages with:

    yum search opencast

This will list all available Opencast distributions in the form `opencast<version>-<dist-type>`

Some available distributions are:

 - opencastXX-allinone
 - opencastXX-admin
 - opencastXX-presentation
 - opencastXX-worker

…where `XX` stands for a specific Opencast version.


Install 3rd-party-tools
-----------------------

This step is optional and only recommended for those who want to build Opencast from source. If you install Opencast
from the repository, all necessary dependencies will be installed automatically.

You can install all necessary 3rd-Party-Tools for Opencast like this:

    yum install ffmpeg tesseract hunspell sox


Uninstall Opencast
--------------------

To uninstall Opencast, you can run:

    yum remove 'opencast*'

This will not touch your created media files or modified configuration files.  If you want to remove them as well, you
have to do that by yourself.

    # Remove media files
    sudo rm -rf /srv/opencast

    # Remove local db, search indexes and working files
    sudo rm -rf /var/lib/opencast

    # Remove configuration files
    sudo rm -rf /etc/opencast

    # Remove logs
    sudo rm -rf /var/log/opencast


Troubleshooting
---------------

### Missing Dependencies

If you try to install Opencast but yum is complaining about missing dependencies, please check if the epel repository is
really activated on your system. Some distributions come with epel preinstalled but disabled. The installation of the
epel-release package will not fix this. You can check what repositories are installed and enabled by executing `yum
repolist enabled` which should give you a list with epel, opencast and opencast-noarch in it. To enable a repository,
edit the configuration file in `/etc/yum.repos.d/`.
