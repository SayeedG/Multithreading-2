//Author: Sayeed Gulmahamad
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.Semaphore;

public class HauntedHouse {

	// binary semaphore mutex. A passenger needs to acquire this mutex before going
	// to travel in a car.
	private static Semaphore mutex = new Semaphore(1);
	// Car list. Contains the list of all of the cars.
	private static LinkedList<Car> carList = new LinkedList<Car>();
	// Total count of the passengers.
	private static int noOfPassengers = 0;
	// Track the count of the passengers that terminated/left.
	private static int noOfTerminatedPassengers = 0;

	// binary semaphore will be used for synchronization purposes
	private static Semaphore syncMutex = new Semaphore(1);

	// binary semaphore mutex.
	private static Semaphore terminateMutex = new Semaphore(1);
	// This contains all of the passengers.
	private static Vector<Passenger> passengerList = new Vector<Passenger>();

	// This will produce a new car
	static class Car extends Thread {
		// The car travel time is measured in milli seconds.
		private static final int CAR_TRAVEL_TIME = 3000;
		// initialize the number of seats for a car
		private int noOfSeats = 0;
		// initialize the capacity for a car
		private int carInitialCapacity = 0;
		// Semaphore for car capacity.
		Semaphore semaphore = null;
		// this list will contain the Passenger's details.
		private LinkedList<Passenger> passengerList = null;
		// Semaphore for carMutex.
		private Semaphore carMutex = null;

		// Counting Semaphore for the car's capacity.
		Semaphore carCapacity = null;
		// tour exit boolean
		boolean exit = false;
		// This boolean will notify the car about tour.
		boolean isTour = true;

		// constructor with arguments
		public Car(int serNo, int capacity) {

			// set the number of seats.
			this.noOfSeats = capacity;
			// set capacity.
			carInitialCapacity = capacity;
			this.setName("Car- " + serNo);
			// set number of permits for Semaphore.
			semaphore = new Semaphore(0);
			// counting semaphore for car capacity.
			carCapacity = new Semaphore(capacity);

			// binary Semaphore
			carMutex = new Semaphore(1);

			// Passenger List.
			passengerList = new LinkedList<Passenger>();
		}

		public void run() {

			try {
				// acquire mutex and wait to load the car.
				carMutex.acquire();

				while (!exit) {

					// startBooking.acquire();
					carMutex.acquire();

					if (isTour) {
						System.out.println(this.getName() + " : tour");
						// start tour.
						Thread.sleep(CAR_TRAVEL_TIME);

						// release all of the passengers.
						releasePassenger();
						System.out.println(this.getName() + " : arrived.");
					}
				}
			} catch (Exception x) {
				x.printStackTrace();
			}
			System.out.println(this.getName() + ": terminate");
		}

		/**
		 * @return noOfSeats
		 */
		public int getNoOfSeats() {
			return noOfSeats;
		}

		/**
		 * @param noOfSeats
		 */
		public void setNoOfSeats(int noOfSeats) {
			this.noOfSeats = noOfSeats;
		}

		/**
		 * This method will load the passenger into car.
		 * 
		 * @param passenger
		 */
		public void acquireSeat(Passenger passenger) {
			// System.out.println("acquireSeat");
			try {
				// get into the car.
				carCapacity.acquire();
				// reduce seat count by as 1 because 1 seat will be filled by passenger.
				this.setNoOfSeats(this.getNoOfSeats() - 1);
				// set passenger status.
				passenger.setPassengerStatus("enter: " + this.getName());
				// increase passenger rides by 1.
				passenger.setRides(passenger.getRides() + 1);
				// add passenger to list.
				passengerList.add(passenger);
				System.out.println(passenger.getPassengerName() + ":rides="
						+ passenger.getRides() + ", "
						+ passenger.getPassengerStatus());

				// Check if all passengers are loaded.
				if (this.getNoOfSeats() == 0) {
					// if all passengers are loaded. Give signal for the tour to start.
					carMutex.release();
				}
				// release mutex so that the other passengers will able to get into the car
				mutex.release();
				// passenger is inside the car. 
				// wait until the tour is completed.
				semaphore.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// stop car
		public void stopCar() {
			// set exit boolean to true to exit the loop.
			this.exit = true;
			// Notify car to not go on any tour.
			this.isTour = false;
			// Release mutex.
			carMutex.release();

		}

		/**
		 * release Passenger after the tour.
		 */
		public void releasePassenger() {
			// Iterate all passengers.
			for (Passenger passenger : passengerList) {
				// set passenger status to exit.
				passenger.setPassengerStatus("exit: " + this.getName());
				System.out.println(passenger.getPassengerName() + ":rides="
						+ passenger.getRides() + ", "
						+ passenger.getPassengerStatus());
				// release the car.
				carCapacity.release();
				// unload passenger.
				semaphore.release();
			}
			// reset capacity.
			this.setNoOfSeats(carInitialCapacity);
			// Clear list.
			passengerList.clear();
		}

	}

	static class Passenger extends Thread {
		// Passenger wandering; time in milliseconds.
		private static final int PASSENGER_WANDER_TIME = 500;
		// initialize passengerName
		private String passengerName;
		// initialize passenger travel status
		private String passengerStatus;

		// initialize the number of rides
		private int rides;

		boolean exit = false;
		// stop mutex
		Semaphore stopMutex = new Semaphore(0);

		// constructor with arguments
		public Passenger(int number) {
			// set passenger name
			this.passengerName = "Passenger-" + number;
			// Set passenger default status to wander.
			passengerStatus = "wander";
		}

		public void run() {

			try {
				while (!exit) {
					// acquire mutex to book a car.
					mutex.acquire();
					// start travel.
					if (travel(this)) {
						// if travel, then wander for few time.
						this.setPassengerStatus("wander");
						System.out.println(this.getPassengerName() + ":rides="
								+ this.getRides() + ", "
								+ this.getPassengerStatus());
						// wandering
						Thread.sleep(PASSENGER_WANDER_TIME);
						// check if passenger has completed max rides.
						if (this.getRides() == 3) {
							// acquire sync to access shared variable
							syncMutex.acquire();
							// increase terminated passenger count.
							noOfTerminatedPassengers++;

							// check to see if it is the last passenger to terminate/leave
							if (noOfTerminatedPassengers == noOfPassengers) {

								// if all passengers completed the tour, then leave
							leaveAllPassengers();
							} else {
								// release
								syncMutex.release();
								// wait for termination
								stopMutex.acquire();
							}
							// release
							syncMutex.release();
						}
					}
				}

			} catch (Exception x) {
				x.printStackTrace();
			}

		}

		/**
		 * stop passenger thread
		 */
		public void stopPassenger() {
			exit = true;
			stopMutex.release();
			// release mutex after termination.
			terminateMutex.release();
		}

		/**
		 * get passengerStatus
		 * 
		 * @return
		 */
		public String getPassengerStatus() {
			return passengerStatus;
		}

		/**
		 * set passengerStatus
		 * 
		 * @param passengerStatus
		 */
		public void setPassengerStatus(String passengerStatus) {
			this.passengerStatus = passengerStatus;
		}

		/**
		 * get passengerName
		 * 
		 * @return
		 */
		public String getPassengerName() {
			return passengerName;
		}

		/**
		 * set passengerName
		 * 
		 * @param passengerName
		 */
		public void setPassengerName(String passengerName) {
			this.passengerName = passengerName;
		}

		/**
		 * get Rides
		 * 
		 * @return
		 */
		public int getRides() {
			return rides;
		}

		/**
		 * set Rides
		 * 
		 * @param rides
		 */
		public void setRides(int rides) {
			this.rides = rides;
		}

	}

	/**
	 * This method will book a car for passengers and start tour.
	 * 
	 * @param passenger
	 * @return
	 */
	public static boolean travel(Passenger passenger) {
		// initialization
		boolean isBooked = false;
		// iterate car list.
		for (Car car : carList) {
			// Check if a seat is available in the car.
			if (car.getNoOfSeats() > 0) {
				// book a seat in the car
				car.acquireSeat(passenger);
				// set to true
				isBooked = true;
				break;
			}
		}
		if (!isBooked) {
			// If not booked, release the mutex and wait for a car to be free.
			mutex.release();
		}
		return isBooked;
	}

	/**
	 * Terminate All Cars
	 */
	public static void terminateAllCars() {
		// Iterate car list
		for (Car car : carList) {
			// Stop car
			car.stopCar();
			}
	}

	/**
	 * Terminate All Passengers.
	 */
	public static void leaveAllPassengers() {
		try {
			// Iterate all passengers.
			for (int j = noOfPassengers - 1; j >= 0; j--) {

				// acquire mutex to leave
				terminateMutex.acquire();
				// terminate passengers.
				System.out.println(passengerList.get(j).getPassengerName()
						+ ": leave");
				// stop passenger.
				passengerList.get(j).stopPassenger();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// terminate all cars.
		terminateAllCars();
		terminateMutex.release();
		syncMutex.release();
	}

	/**
	 * main programme.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		for (int i = 1; i <= 3; i++) {
			Car car = new Car(i, 4);
			carList.add(car);
			car.start();
		}
		for (int i = 1; i <= 11; i++) {
			noOfPassengers++;
			Passenger passenger1 = new Passenger(i);
			passengerList.add(passenger1);
			passenger1.start();

		}
	}

}
